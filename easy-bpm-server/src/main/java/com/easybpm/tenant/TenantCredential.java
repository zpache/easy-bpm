package com.easybpm.tenant;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.easybpm.common.crypto.EncryptedStringTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 接入凭证。该表不参与租户插件过滤，因为认证时租户尚未确定。
 *
 * <p>app_secret 落库加密（AES-256-GCM），验签时自动解密后参与 HMAC 计算；
 * 控制台接口始终返回掩码，明文只在创建/轮换时返回一次。
 */
@Data
@TableName(value = "bpm_tenant_credential", autoResultMap = true)
public class TenantCredential {

    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    private String name;
    private String appKey;

    @TableField(typeHandler = EncryptedStringTypeHandler.class)
    private String appSecret;

    private String status;
    private LocalDateTime expireAt;
    private LocalDateTime lastUsedAt;
    private LocalDateTime createdAt;
}
