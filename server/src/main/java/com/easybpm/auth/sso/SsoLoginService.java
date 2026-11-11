package com.easybpm.auth.sso;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.easybpm.audit.AuditService;
import com.easybpm.auth.AuthPrincipal;
import com.easybpm.auth.ConsoleAuthService;
import com.easybpm.auth.ConsoleUser;
import com.easybpm.auth.ConsoleUserMapper;
import com.easybpm.auth.JwtService;
import com.easybpm.common.BizException;
import com.easybpm.common.ErrorCode;
import com.easybpm.common.Ids;
import com.easybpm.tenant.Tenant;
import com.easybpm.tenant.TenantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * SSO 登录主体流程：登录页探测 → 授权跳转 → 回调校验 → 身份绑定 → 签发本地 JWT。
 *
 * <p>账号策略（M1）：**默认拒绝**。IdP 认证通过但本地没有绑定关系时，只登记一条 PENDING
 * 身份记录并把 subject/姓名/邮箱 留给管理员识别，不自动建号。这样即使 IdP 侧任何人都能认证，
 * 也进不了控制台。
 *
 * <p>权限天花板：SSO 只可能登录到已存在的控制台账号，无法获得 PLATFORM_ADMIN（除非管理员
 * 手工把该账号设成平台管理员）。IdP 的 groups/roles 不参与授权。
 */
@Service
public class SsoLoginService {

    private static final Logger log = LoggerFactory.getLogger(SsoLoginService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String DEFAULT_PROVIDER_CODE = "default";

    private final SsoProviderMapper providerMapper;
    private final SsoIdentityMapper identityMapper;
    private final ConsoleUserMapper consoleUserMapper;
    private final TenantService tenantService;
    private final OidcClient oidc;
    private final JwtService jwtService;
    private final SsoTicketStore tickets;
    private final AuditService audit;

    private final boolean enabled;
    private final String consoleBaseUrl;
    private final String defaultRedirectUri;

    public SsoLoginService(SsoProviderMapper providerMapper, SsoIdentityMapper identityMapper,
                           ConsoleUserMapper consoleUserMapper, TenantService tenantService,
                           OidcClient oidc, JwtService jwtService, SsoTicketStore tickets, AuditService audit,
                           @Value("${easybpm.sso.enabled:true}") boolean enabled,
                           @Value("${easybpm.sso.console-base-url:http://localhost:8091}") String consoleBaseUrl,
                           @Value("${easybpm.sso.default-redirect-uri:http://localhost:8091/api/auth/sso/callback}")
                           String defaultRedirectUri) {
        this.providerMapper = providerMapper;
        this.identityMapper = identityMapper;
        this.consoleUserMapper = consoleUserMapper;
        this.tenantService = tenantService;
        this.oidc = oidc;
        this.jwtService = jwtService;
        this.tickets = tickets;
        this.audit = audit;
        this.enabled = enabled;
        this.consoleBaseUrl = trimTrailingSlash(consoleBaseUrl);
        this.defaultRedirectUri = defaultRedirectUri;
    }

    /** 登录页探测：只暴露是否启用与展示名，不泄漏 clientId / issuer 等配置。 */
    public List<ProviderBrief> providers(String tenantCode) {
        if (!enabled || tenantCode == null || tenantCode.isBlank()) {
            return List.of();
        }
        Tenant tenant;
        try {
            tenant = tenantService.requireByCode(tenantCode.trim());
        } catch (BizException e) {
            // 登录页探测不区分「租户不存在」与「未配置 SSO」，避免枚举租户
            return List.of();
        }
        return providerMapper.selectList(new LambdaQueryWrapper<SsoProvider>()
                        .eq(SsoProvider::getTenantId, tenant.getId())
                        .eq(SsoProvider::getStatus, "ACTIVE")
                        .orderByAsc(SsoProvider::getCode))
                .stream()
                .map(p -> new ProviderBrief(p.getCode(), p.getName()))
                .toList();
    }

    public record ProviderBrief(String code, String name) {
    }

    /** 生成授权跳转 URL，并把 state/nonce/PKCE verifier 暂存到服务端。 */
    public String authorizeUrl(String tenantCode, String providerCode, String redirectPath) {
        if (!enabled) {
            throw new BizException(ErrorCode.FORBIDDEN, "未开启单点登录");
        }
        Tenant tenant = tenantService.requireByCode(tenantCode);
        SsoProvider provider = requireActiveProvider(tenant.getId(),
                providerCode == null || providerCode.isBlank() ? DEFAULT_PROVIDER_CODE : providerCode);

        String state = randomToken(32);
        String nonce = randomToken(16);
        String codeVerifier = randomToken(48);
        String codeChallenge = base64Url(sha256(codeVerifier));

        tickets.putState(state, new SsoTicketStore.StateEntry(tenant.getId(), provider.getId(),
                safeRedirect(redirectPath), nonce, codeVerifier, System.currentTimeMillis() + 5 * 60_000L));

        OidcMetadata metadata = oidc.discover(provider.getIssuer());
        return oidc.authorizationUrl(metadata, provider.getClientId(), provider.getScopes(),
                redirectUri(provider), state, nonce, codeChallenge);
    }

    /**
     * 处理 IdP 回调。无论成功失败都返回前端回调页地址，由浏览器 302 过去，
     * 不把错误 JSON 直接抛给用户。
     */
    public String callback(String code, String state) {
        SsoTicketStore.StateEntry entry = tickets.consumeState(state);
        if (entry == null) {
            return errorRedirect("STATE_INVALID", "登录链接已失效，请返回登录页重新发起");
        }
        SsoProvider provider = providerMapper.selectById(entry.providerId());
        if (provider == null || !"ACTIVE".equals(provider.getStatus())
                || !provider.getTenantId().equals(entry.tenantId())) {
            return errorRedirect("PROVIDER_UNAVAILABLE", "该租户的单点登录已停用");
        }
        if (code == null || code.isBlank()) {
            return errorRedirect("CODE_MISSING", "IdP 未返回授权码，请重试");
        }
        try {
            ConsoleAuthService.LoginResult result = login(provider, code, entry);
            String ticket = randomToken(32);
            tickets.putTicket(ticket, result);
            return consoleBaseUrl + "/sso/callback?ticket=" + urlEncode(ticket)
                    + (entry.redirectPath() == null ? "" : "&redirect=" + urlEncode(entry.redirectPath()));
        } catch (SsoFlowException e) {
            return errorRedirect(e.code, e.getMessage());
        } catch (BizException e) {
            log.warn("SSO 回调失败 provider={}：{}", provider.getId(), e.getMessage());
            return errorRedirect("IDP_ERROR", e.getMessage());
        } catch (RuntimeException e) {
            log.error("SSO 回调异常 provider={}", provider.getId(), e);
            return errorRedirect("IDP_ERROR", "单点登录失败，请稍后重试或使用密码登录");
        }
    }

    /** 一次性 ticket 换本地 JWT。 */
    public ConsoleAuthService.LoginResult exchange(String ticket) {
        ConsoleAuthService.LoginResult result = tickets.consumeTicket(ticket);
        if (result == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "登录凭证已失效，请重新登录");
        }
        return result;
    }

    private ConsoleAuthService.LoginResult login(SsoProvider provider, String code,
                                                 SsoTicketStore.StateEntry entry) {
        OidcMetadata metadata = oidc.discover(provider.getIssuer());
        OidcClient.TokenResponse token = oidc.exchangeCode(metadata, provider, code,
                entry.codeVerifier(), redirectUri(provider));
        Map<String, Object> claims = oidc.verifyIdToken(metadata, provider, token.idToken(), entry.nonce());
        Map<String, Object> userinfo = oidc.userInfo(metadata, token.accessToken());

        String subject = firstNonBlank(str(claims.get("sub")), str(userinfo.get("sub")));
        if (subject == null || subject.isBlank()) {
            throw new SsoFlowException("SUBJECT_MISSING", "IdP 未返回用户唯一标识（sub）");
        }
        String email = claim(provider.getEmailClaim(), claims, userinfo, "email", "mail", "preferred_username");
        String displayName = claim(provider.getNameClaim(), claims, userinfo,
                "name", "display_name", "nickname", "preferred_username");

        SsoIdentity identity = findBySubject(provider.getId(), subject);
        LocalDateTime now = LocalDateTime.now();
        if (identity == null) {
            identity = new SsoIdentity();
            identity.setId(Ids.id());
            identity.setTenantId(provider.getTenantId());
            identity.setProviderId(provider.getId());
            identity.setSubject(subject);
            identity.setIssuer(provider.getIssuer());
            identity.setEmail(email);
            identity.setDisplayName(displayName);
            identity.setStatus(SsoIdentity.PENDING);
            identity.setLastAttemptAt(now);
            identity.setCreatedAt(now);
            try {
                identityMapper.insert(identity);
                auditAs(provider.getTenantId(), subject, displayName, "SSO_BIND_REQUEST", identity.getId(),
                        "首次通过 IdP 认证，等待管理员绑定：email=" + email);
            } catch (DuplicateKeyException e) {
                // 并发首登：另一请求已插入，回读继续走绑定校验
                identity = findBySubject(provider.getId(), subject);
            }
        }
        if (identity == null) {
            throw new SsoFlowException("ACCOUNT_NOT_BOUND", "身份登记失败，请联系管理员");
        }
        if (SsoIdentity.DISABLED.equals(identity.getStatus())) {
            throw new SsoFlowException("ACCOUNT_DISABLED", "该企业身份已被管理员解绑，请联系管理员");
        }

        String consoleUserId = identity.getConsoleUserId();
        if (consoleUserId == null || consoleUserId.isBlank()) {
            touchAttempt(identity, email, displayName, now);
            throw new SsoFlowException("ACCOUNT_NOT_BOUND",
                    "已通过企业身份认证，但尚未开通控制台账号，请联系管理员绑定后重试");
        }

        ConsoleUser user = consoleUserMapper.selectById(consoleUserId);
        if (user == null || !provider.getTenantId().equals(user.getTenantId())) {
            touchAttempt(identity, email, displayName, now);
            throw new SsoFlowException("ACCOUNT_NOT_BOUND", "绑定的控制台账号不存在，请联系管理员重新绑定");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new SsoFlowException("ACCOUNT_DISABLED", "控制台账号已停用，请联系管理员");
        }

        // 登录成功：刷新身份与账号的登录痕迹
        SsoIdentity identityUpdate = new SsoIdentity();
        identityUpdate.setId(identity.getId());
        identityUpdate.setStatus(SsoIdentity.ACTIVE);
        identityUpdate.setEmail(email);
        identityUpdate.setDisplayName(displayName);
        identityUpdate.setExternalUserId(user.getExternalUserId());
        identityUpdate.setLastAttemptAt(now);
        identityUpdate.setLastLoginAt(now);
        identityMapper.updateById(identityUpdate);

        ConsoleUser userUpdate = new ConsoleUser();
        userUpdate.setId(user.getId());
        userUpdate.setLastLoginAt(now);
        consoleUserMapper.updateById(userUpdate);

        AuthPrincipal principal = new AuthPrincipal(user.getId(), provider.getTenantId(),
                user.getDisplayName(), user.getRole(), user.getExternalUserId());
        String localToken = jwtService.issue(principal);
        Tenant tenant = tenantService.requireById(provider.getTenantId());
        auditAs(provider.getTenantId(), user.getId(), user.getDisplayName(), "SSO_LOGIN", user.getId(),
                "provider=" + provider.getCode() + " subject=" + subject + " username=" + user.getUsername());
        return new ConsoleAuthService.LoginResult(localToken, jwtService.ttlSeconds(), principal,
                tenant.getCode(), tenant.getName(), user.getUsername());
    }

    private SsoIdentity findBySubject(String providerId, String subject) {
        return identityMapper.selectOne(new LambdaQueryWrapper<SsoIdentity>()
                .eq(SsoIdentity::getProviderId, providerId)
                .eq(SsoIdentity::getSubject, subject));
    }

    private void touchAttempt(SsoIdentity identity, String email, String displayName, LocalDateTime now) {
        SsoIdentity update = new SsoIdentity();
        update.setId(identity.getId());
        update.setStatus(SsoIdentity.PENDING);
        update.setEmail(email);
        update.setDisplayName(displayName);
        update.setLastAttemptAt(now);
        identityMapper.updateById(update);
    }

    private SsoProvider requireActiveProvider(String tenantId, String code) {
        SsoProvider provider = providerMapper.selectOne(new LambdaQueryWrapper<SsoProvider>()
                .eq(SsoProvider::getTenantId, tenantId)
                .eq(SsoProvider::getCode, code));
        if (provider == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "该租户未配置单点登录");
        }
        if (!"ACTIVE".equals(provider.getStatus())) {
            throw new BizException(ErrorCode.FORBIDDEN, "该租户的单点登录已停用");
        }
        return provider;
    }

    private void auditAs(String tenantId, String operatorId, String operatorName,
                         String action, String targetId, String detail) {
        audit.recordAs(tenantId, operatorId, operatorName, action, "SSO_IDENTITY", targetId, detail);
    }

    private String redirectUri(SsoProvider provider) {
        return provider.getRedirectUri() == null || provider.getRedirectUri().isBlank()
                ? defaultRedirectUri : provider.getRedirectUri().trim();
    }

    private String errorRedirect(String code, String message) {
        return consoleBaseUrl + "/sso/callback?error=" + urlEncode(code)
                + "&message=" + urlEncode(message == null ? "单点登录失败" : message);
    }

    /** 只允许站内相对路径，拒绝 //evil.com 这类协议相对地址，杜绝开放重定向。 */
    private static String safeRedirect(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String trimmed = path.trim();
        if (!trimmed.startsWith("/") || trimmed.startsWith("//") || trimmed.contains("\\")) {
            return null;
        }
        return trimmed.length() > 512 ? null : trimmed;
    }

    private static String claim(String preferred, Map<String, Object> claims,
                                Map<String, Object> userinfo, String... fallbacks) {
        if (preferred != null && !preferred.isBlank()) {
            String value = firstNonBlank(str(claims.get(preferred.trim())), str(userinfo.get(preferred.trim())));
            if (value != null) {
                return value;
            }
        }
        for (String key : fallbacks) {
            String value = firstNonBlank(str(claims.get(key)), str(userinfo.get(key)));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String randomToken(int bytes) {
        byte[] buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        return base64Url(buffer);
    }

    private static String base64Url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String trimTrailingSlash(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    /** 登录流程内的可预期失败：带业务码，供前端回调页分类展示。 */
    private static final class SsoFlowException extends RuntimeException {

        private final String code;

        SsoFlowException(String code, String message) {
            super(message);
            this.code = code;
        }
    }
}
