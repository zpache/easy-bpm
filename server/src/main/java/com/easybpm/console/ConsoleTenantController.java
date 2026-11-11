package com.easybpm.console;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.easybpm.audit.AuditService;
import com.easybpm.auth.CurrentUser;
import com.easybpm.common.ApiResponse;
import com.easybpm.common.BizException;
import com.easybpm.common.ErrorCode;
import com.easybpm.common.Ids;
import com.easybpm.tenant.Tenant;
import com.easybpm.tenant.TenantCredential;
import com.easybpm.tenant.TenantCredentialMapper;
import com.easybpm.tenant.TenantMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 控制台租户信息与接入凭证管理。
 *
 * <p>AppSecret 只在创建与轮换时完整返回一次，列表仅显示掩码；
 * 凭证表不参与租户插件隔离（认证前置），因此这里全部手动加 tenantId 条件。
 */
@RestController
@RequestMapping("/api/tenant")
public class ConsoleTenantController {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private final TenantMapper tenantMapper;
    private final TenantCredentialMapper credentialMapper;
    private final AuditService audit;

    public ConsoleTenantController(TenantMapper tenantMapper, TenantCredentialMapper credentialMapper,
                                   AuditService audit) {
        this.tenantMapper = tenantMapper;
        this.credentialMapper = credentialMapper;
        this.audit = audit;
    }

    @GetMapping("/info")
    public ApiResponse<TenantInfo> info() {
        Tenant tenant = tenantMapper.selectById(CurrentUser.require().tenantId());
        if (tenant == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "租户不存在");
        }
        return ApiResponse.ok(new TenantInfo(tenant.getCode(), tenant.getName(), tenant.getStatus(),
                tenant.getRemark(), tenant.getCreatedAt()));
    }

    @GetMapping("/credentials")
    public ApiResponse<List<CredentialView>> credentials() {
        requireAdmin();
        List<TenantCredential> list = credentialMapper.selectList(new LambdaQueryWrapper<TenantCredential>()
                .eq(TenantCredential::getTenantId, CurrentUser.require().tenantId())
                .orderByDesc(TenantCredential::getCreatedAt));
        return ApiResponse.ok(list.stream().map(c -> new CredentialView(
                c.getId(), c.getName(), c.getAppKey(), mask(c.getAppSecret()), c.getStatus(),
                c.getExpireAt(), c.getLastUsedAt(), c.getCreatedAt())).toList());
    }

    @PostMapping("/credentials")
    public ApiResponse<CredentialSecretView> create(@Valid @RequestBody CreateCredentialRequest request) {
        requireAdmin();
        String tenantId = CurrentUser.require().tenantId();
        TenantCredential credential = new TenantCredential();
        credential.setId(Ids.id());
        credential.setTenantId(tenantId);
        credential.setName(request.name());
        credential.setAppKey("ak_" + randomHex(16));
        credential.setAppSecret("sk_" + randomHex(32));
        credential.setStatus("ACTIVE");
        credential.setExpireAt(request.expireAt());
        credential.setCreatedAt(LocalDateTime.now());
        // AppKey 唯一索引兜底，随机空间下碰撞概率可忽略，不做重试
        credentialMapper.insert(credential);
        audit.record("CREDENTIAL_CREATE", "CREDENTIAL", credential.getId(),
                "新建凭证「" + credential.getName() + "」，AppKey=" + credential.getAppKey());
        return ApiResponse.ok(toSecretView(credential));
    }

    @PostMapping("/credentials/{id}/rotate")
    public ApiResponse<CredentialSecretView> rotate(@PathVariable String id) {
        requireAdmin();
        TenantCredential credential = requireOwned(id);
        credential.setAppSecret("sk_" + randomHex(32));
        credential.setStatus("ACTIVE");
        credentialMapper.updateById(credential);
        audit.record("CREDENTIAL_ROTATE", "CREDENTIAL", credential.getId(),
                "轮换凭证「" + credential.getName() + "」，AppKey=" + credential.getAppKey());
        return ApiResponse.ok(toSecretView(credential));
    }

    @PostMapping("/credentials/{id}/disable")
    public ApiResponse<Void> disable(@PathVariable String id) {
        requireAdmin();
        TenantCredential credential = requireOwned(id);
        credential.setStatus("DISABLED");
        credentialMapper.updateById(credential);
        audit.record("CREDENTIAL_DISABLE", "CREDENTIAL", credential.getId(),
                "停用凭证「" + credential.getName() + "」");
        return ApiResponse.ok();
    }

    /**
     * 启用已停用的凭证。AppKey / AppSecret 保持不变，接入方无需改配置。
     * 已过期的凭证不允许直接启用（否则调用仍会被鉴权拒绝），需先轮换密钥重新签发。
     */
    @PostMapping("/credentials/{id}/enable")
    public ApiResponse<Void> enable(@PathVariable String id) {
        requireAdmin();
        TenantCredential credential = requireOwned(id);
        if ("ACTIVE".equals(credential.getStatus())) {
            return ApiResponse.ok();
        }
        if (credential.getExpireAt() != null && credential.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new BizException(ErrorCode.CONFLICT, "凭证已过期，请轮换密钥并设置新的过期时间后再启用");
        }
        credential.setStatus("ACTIVE");
        credentialMapper.updateById(credential);
        audit.record("CREDENTIAL_ENABLE", "CREDENTIAL", credential.getId(),
                "启用凭证「" + credential.getName() + "」，AppKey=" + credential.getAppKey());
        return ApiResponse.ok();
    }

    private TenantCredential requireOwned(String id) {
        TenantCredential credential = credentialMapper.selectById(id);
        if (credential == null || !credential.getTenantId().equals(CurrentUser.require().tenantId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "凭证不存在");
        }
        return credential;
    }

    private void requireAdmin() {
        if (!CurrentUser.require().isTenantAdmin()) {
            throw new BizException(ErrorCode.FORBIDDEN, "需要租户管理员权限");
        }
    }

    private CredentialSecretView toSecretView(TenantCredential credential) {
        return new CredentialSecretView(credential.getId(), credential.getName(), credential.getAppKey(),
                credential.getAppSecret(), credential.getCreatedAt());
    }

    private String mask(String secret) {
        return secret == null || secret.length() <= 8 ? "****" : secret.substring(0, 7) + "****";
    }

    private String randomHex(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(HEX[RANDOM.nextInt(HEX.length)]);
        }
        return builder.toString();
    }

    public record CreateCredentialRequest(@NotBlank String name, LocalDateTime expireAt) {
    }

    public record TenantInfo(String code, String name, String status, String remark, LocalDateTime createdAt) {
    }

    public record CredentialView(String id, String name, String appKey, String appSecretMasked, String status,
                                 LocalDateTime expireAt, LocalDateTime lastUsedAt, LocalDateTime createdAt) {
    }

    public record CredentialSecretView(String id, String name, String appKey, String appSecret,
                                       LocalDateTime createdAt) {
    }
}
