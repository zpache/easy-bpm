package com.easybpm.auth.sso;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.easybpm.common.crypto.EncryptedStringTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 租户级单点登录 IdP 配置。
 *
 * <p>该表不参与租户插件自动隔离：授权请求发生在登录之前，此时还没有租户上下文，
 * 只能先按 tenant_code 反查租户再手动按 tenant_id 过滤。所有查询点必须显式带 tenantId。
 *
 * <p>{@code clientSecret} 落库经 AES-256-GCM 加密（{@link EncryptedStringTypeHandler}），
 * 接口返回一律脱敏。
 */
@Data
@TableName(value = "bpm_sso_provider", autoResultMap = true)
public class SsoProvider {

    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    /** 同一租户内的提供方编码，M1 固定 default，为多 IdP 预留 */
    private String code;
    private String name;
    /** OIDC；SAML 在后续版本支持 */
    private String protocol;
    private String issuer;
    private String clientId;

    @TableField(typeHandler = EncryptedStringTypeHandler.class)
    private String clientSecret;

    private String scopes;
    /** 为空时使用 easybpm.sso.default-redirect-uri */
    private String redirectUri;
    /** 自定义 claim 名，为空时按常见字段兜底探测 */
    private String nameClaim;
    private String emailClaim;
    private String status;
    /** 1 表示 IdP 认证通过后自动建号（M2 启用，M1 固定 0：默认拒绝，管理员手工开通） */
    private Integer autoProvision;
    private String defaultRole;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
