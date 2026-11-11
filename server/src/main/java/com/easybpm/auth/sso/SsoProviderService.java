package com.easybpm.auth.sso;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.easybpm.audit.AuditService;
import com.easybpm.auth.ConsoleUser;
import com.easybpm.auth.ConsoleUserMapper;
import com.easybpm.auth.CurrentUser;
import com.easybpm.common.BizException;
import com.easybpm.common.ErrorCode;
import com.easybpm.common.Ids;
import com.easybpm.tenant.Tenant;
import com.easybpm.tenant.TenantMapper;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 平台端的 SSO 配置与身份绑定管理。
 *
 * <p>配置入口只开放给平台管理员：IdP 的 client_secret 写在租户侧，如果租户管理员也能改，
 * 就可能把登录指向自己的 IdP 从而接管账号。绑定操作同样在平台侧，避免「谁都能给自己开号」。
 */
@Service
public class SsoProviderService {

    private static final Logger log = LoggerFactory.getLogger(SsoProviderService.class);
    private static final String DEFAULT_CODE = "default";
    private static final Set<String> STATUSES = Set.of("ACTIVE", "DISABLED");
    private static final String SECRET_MASK = "******";

    private final SsoProviderMapper providerMapper;
    private final SsoIdentityMapper identityMapper;
    private final ConsoleUserMapper consoleUserMapper;
    private final TenantMapper tenantMapper;
    private final OidcClient oidc;
    private final AuditService audit;

    public SsoProviderService(SsoProviderMapper providerMapper, SsoIdentityMapper identityMapper,
                              ConsoleUserMapper consoleUserMapper, TenantMapper tenantMapper,
                              OidcClient oidc, AuditService audit) {
        this.providerMapper = providerMapper;
        this.identityMapper = identityMapper;
        this.consoleUserMapper = consoleUserMapper;
        this.tenantMapper = tenantMapper;
        this.oidc = oidc;
        this.audit = audit;
    }

    public SsoConfigView get(String tenantId) {
        requirePlatformAdmin();
        SsoProvider provider = find(tenantId);
        return provider == null ? SsoConfigView.empty() : toView(provider);
    }

    @Transactional
    public SsoConfigView save(String tenantId, SaveRequest request) {
        requirePlatformAdmin();
        requireTenant(tenantId);
        SsoProvider provider = find(tenantId);
        boolean creating = provider == null;
        if (creating) {
            provider = new SsoProvider();
            provider.setId(Ids.id());
            provider.setTenantId(tenantId);
            provider.setCode(DEFAULT_CODE);
            provider.setProtocol("OIDC");
            provider.setAutoProvision(0);
            provider.setDefaultRole("MEMBER");
            provider.setCreatedAt(LocalDateTime.now());
        }

        provider.setName(trimOr(request.name(), creating ? "企业登录" : provider.getName()));
        provider.setIssuer(requireHttpUrl(request.issuer()));
        provider.setClientId(trimOr(request.clientId(), provider.getClientId()));
        provider.setScopes(trimOr(request.scopes(), "openid profile email"));
        provider.setRedirectUri(blankToNull(request.redirectUri()));
        provider.setNameClaim(blankToNull(request.nameClaim()));
        provider.setEmailClaim(blankToNull(request.emailClaim()));
        provider.setStatus(STATUSES.contains(request.status()) ? request.status() : "ACTIVE");
        provider.setUpdatedAt(LocalDateTime.now());

        if (provider.getClientId() == null || provider.getClientId().isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "client_id 不能为空");
        }
        // 密钥留空表示不修改：编辑页拿到的是掩码，不应把掩码写回库
        if (request.clientSecret() != null && !request.clientSecret().isBlank()
                && !SECRET_MASK.equals(request.clientSecret())) {
            provider.setClientSecret(request.clientSecret().trim());
        }
        if (creating && (provider.getClientSecret() == null || provider.getClientSecret().isBlank())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "client_secret 不能为空");
        }

        if (creating) {
            providerMapper.insert(provider);
        } else {
            providerMapper.updateById(provider);
        }
        audit.record("SSO_PROVIDER_SAVE", "TENANT", tenantId,
                (creating ? "新建" : "修改") + " SSO 配置：issuer=" + provider.getIssuer()
                        + " clientId=" + provider.getClientId() + " status=" + provider.getStatus());
        return toView(provider);
    }

    /** 测试连接：只做 discovery，验证 issuer 可达与文档自洽，不发起真实登录。 */
    public TestResult test(String tenantId, String issuer) {
        requirePlatformAdmin();
        try {
            OidcMetadata metadata = oidc.test(requireHttpUrl(issuer));
            return new TestResult(true, "连接成功", metadata.issuer(), metadata.authorizationEndpoint(),
                    metadata.tokenEndpoint(), metadata.jwksUri(), metadata.userinfoEndpoint());
        } catch (BizException e) {
            return new TestResult(false, e.getMessage(), null, null, null, null, null);
        }
    }

    public List<IdentityView> identities(String tenantId, String status) {
        requirePlatformAdmin();
        SsoProvider provider = find(tenantId);
        if (provider == null) {
            return List.of();
        }
        LambdaQueryWrapper<SsoIdentity> query = new LambdaQueryWrapper<SsoIdentity>()
                .eq(SsoIdentity::getTenantId, tenantId)
                .eq(SsoIdentity::getProviderId, provider.getId())
                .orderByDesc(SsoIdentity::getLastAttemptAt)
                .orderByDesc(SsoIdentity::getCreatedAt);
        if (status != null && !status.isBlank()) {
            query.eq(SsoIdentity::getStatus, status.trim().toUpperCase());
        }
        List<SsoIdentity> list = identityMapper.selectList(query);
        return list.stream().map(identity -> {
            ConsoleUser user = identity.getConsoleUserId() == null ? null
                    : consoleUserMapper.selectById(identity.getConsoleUserId());
            return new IdentityView(identity.getId(), identity.getSubject(), identity.getEmail(),
                    identity.getDisplayName(), identity.getStatus(),
                    user == null ? null : user.getUsername(),
                    user == null ? null : user.getDisplayName(),
                    identity.getLastAttemptAt(), identity.getLastLoginAt());
        }).toList();
    }

    /** 可绑定的本地账号：该租户 ACTIVE 且尚未被同一 IdP 绑定的控制台账号。 */
    public List<CandidateView> candidates(String tenantId) {
        requirePlatformAdmin();
        SsoProvider provider = find(tenantId);
        Set<String> bound = provider == null ? Set.of() : identityMapper.selectList(
                        new LambdaQueryWrapper<SsoIdentity>()
                                .eq(SsoIdentity::getProviderId, provider.getId())
                                .isNotNull(SsoIdentity::getConsoleUserId)
                                .eq(SsoIdentity::getStatus, SsoIdentity.ACTIVE))
                .stream().map(SsoIdentity::getConsoleUserId).filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        return consoleUserMapper.selectList(new LambdaQueryWrapper<ConsoleUser>()
                        .eq(ConsoleUser::getTenantId, tenantId)
                        .eq(ConsoleUser::getStatus, "ACTIVE")
                        .orderByAsc(ConsoleUser::getUsername))
                .stream()
                .filter(user -> !bound.contains(user.getId()))
                .map(user -> new CandidateView(user.getId(), user.getUsername(), user.getDisplayName(),
                        user.getExternalUserId(), user.getAuthSource()))
                .toList();
    }

    @Transactional
    public IdentityView bind(String tenantId, String identityId, BindRequest request) {
        requirePlatformAdmin();
        SsoProvider provider = find(tenantId);
        if (provider == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "该租户尚未配置单点登录");
        }
        SsoIdentity identity = requireIdentity(tenantId, identityId, provider.getId());
        ConsoleUser user = consoleUserMapper.selectOne(new LambdaQueryWrapper<ConsoleUser>()
                .eq(ConsoleUser::getTenantId, tenantId)
                .eq(ConsoleUser::getUsername, request.username().trim()));
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "控制台账号不存在：" + request.username());
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BizException(ErrorCode.CONFLICT, "该控制台账号已停用，请先启用");
        }
        Long conflict = identityMapper.selectCount(new LambdaQueryWrapper<SsoIdentity>()
                .eq(SsoIdentity::getProviderId, provider.getId())
                .eq(SsoIdentity::getConsoleUserId, user.getId())
                .eq(SsoIdentity::getStatus, SsoIdentity.ACTIVE)
                .ne(SsoIdentity::getId, identity.getId()));
        if (conflict != null && conflict > 0) {
            throw new BizException(ErrorCode.CONFLICT,
                    "账号 " + user.getUsername() + " 已绑定其他企业身份，请先解绑原身份");
        }

        identity.setConsoleUserId(user.getId());
        identity.setExternalUserId(user.getExternalUserId());
        identity.setStatus(SsoIdentity.ACTIVE);
        identityMapper.updateById(identity);

        // 可选：绑定后清空密码，让该账号只能走企业登录（SSO-only）
        if (Boolean.TRUE.equals(request.ssoOnly())) {
            if ("PLATFORM_ADMIN".equals(user.getRole())) {
                throw new BizException(ErrorCode.BAD_REQUEST,
                        "平台管理员不允许设为 SSO 专属账号，避免 IdP 故障时无法进入平台管理");
            }
            consoleUserMapper.update(null, new LambdaUpdateWrapper<ConsoleUser>()
                    .eq(ConsoleUser::getId, user.getId())
                    .set(ConsoleUser::getPasswordHash, null)
                    .set(ConsoleUser::getAuthSource, "SSO"));
        }

        audit.record("SSO_BIND", "CONSOLE_USER", user.getId(),
                "绑定企业身份 subject=" + identity.getSubject() + " → " + user.getUsername()
                        + (Boolean.TRUE.equals(request.ssoOnly()) ? "（已清空本地密码，仅允许企业登录）" : ""));
        return new IdentityView(identity.getId(), identity.getSubject(), identity.getEmail(),
                identity.getDisplayName(), identity.getStatus(), user.getUsername(), user.getDisplayName(),
                identity.getLastAttemptAt(), identity.getLastLoginAt());
    }

    /** 解绑：保留记录并置 DISABLED，便于审计追溯；重新绑定走 bind 即可复用同一条记录。 */
    @Transactional
    public IdentityView unbind(String tenantId, String identityId) {
        requirePlatformAdmin();
        SsoProvider provider = find(tenantId);
        if (provider == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "该租户尚未配置单点登录");
        }
        SsoIdentity identity = requireIdentity(tenantId, identityId, provider.getId());
        identity.setStatus(SsoIdentity.DISABLED);
        identityMapper.updateById(identity);
        audit.record("SSO_UNBIND", "SSO_IDENTITY", identity.getId(),
                "解绑企业身份 subject=" + identity.getSubject());
        return new IdentityView(identity.getId(), identity.getSubject(), identity.getEmail(),
                identity.getDisplayName(), identity.getStatus(), null, null,
                identity.getLastAttemptAt(), identity.getLastLoginAt());
    }

    private SsoIdentity requireIdentity(String tenantId, String identityId, String providerId) {
        SsoIdentity identity = identityMapper.selectById(identityId);
        if (identity == null || !tenantId.equals(identity.getTenantId())
                || !providerId.equals(identity.getProviderId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "身份记录不存在");
        }
        return identity;
    }

    private SsoProvider find(String tenantId) {
        return providerMapper.selectOne(new LambdaQueryWrapper<SsoProvider>()
                .eq(SsoProvider::getTenantId, tenantId)
                .eq(SsoProvider::getCode, DEFAULT_CODE));
    }

    private void requireTenant(String tenantId) {
        Tenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "租户不存在");
        }
        if ("platform".equals(tenant.getCode())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "平台租户不使用租户级 SSO");
        }
    }

    private void requirePlatformAdmin() {
        if (!CurrentUser.require().isPlatformAdmin()) {
            throw new BizException(ErrorCode.FORBIDDEN, "需要平台管理员权限");
        }
    }

    private static String requireHttpUrl(String url) {
        String value = blankToNull(url);
        if (value == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "issuer 不能为空");
        }
        if (!value.startsWith("https://") && !value.startsWith("http://")) {
            throw new BizException(ErrorCode.BAD_REQUEST, "issuer 必须是 http(s) 地址");
        }
        return value;
    }

    private static String trimOr(String value, String fallback) {
        String trimmed = blankToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private SsoConfigView toView(SsoProvider provider) {
        return new SsoConfigView(true, provider.getId(), provider.getCode(), provider.getName(),
                provider.getProtocol(), provider.getIssuer(), provider.getClientId(),
                provider.getClientSecret() == null ? null : SECRET_MASK,
                provider.getScopes(), provider.getRedirectUri(), provider.getNameClaim(),
                provider.getEmailClaim(), provider.getStatus(), provider.getAutoProvision(),
                provider.getDefaultRole(), provider.getUpdatedAt());
    }

    public record SaveRequest(String name, String issuer, String clientId, String clientSecret,
                              String scopes, String redirectUri, String nameClaim, String emailClaim,
                              String status) {
    }

    public record BindRequest(@NotBlank String username, Boolean ssoOnly) {
    }

    public record SsoConfigView(boolean configured, String id, String code, String name, String protocol,
                                String issuer, String clientId, String clientSecret, String scopes,
                                String redirectUri, String nameClaim, String emailClaim, String status,
                                Integer autoProvision, String defaultRole, LocalDateTime updatedAt) {

        static SsoConfigView empty() {
            return new SsoConfigView(false, null, DEFAULT_CODE, null, "OIDC", null, null, null,
                    "openid profile email", null, null, null, "ACTIVE", 0, "MEMBER", null);
        }
    }

    public record IdentityView(String id, String subject, String email, String displayName, String status,
                               String username, String consoleDisplayName,
                               LocalDateTime lastAttemptAt, LocalDateTime lastLoginAt) {
    }

    public record CandidateView(String id, String username, String displayName,
                                String externalUserId, String authSource) {
    }

    public record TestResult(boolean ok, String message, String issuer, String authorizationEndpoint,
                             String tokenEndpoint, String jwksUri, String userinfoEndpoint) {
    }
}
