package com.easybpm.tenant;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 接入凭证。该表不参与租户插件过滤，因为认证时租户尚未确定。
 */
@Data
@TableName("bpm_tenant_credential")
public class TenantCredential {

    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    private String name;
    private String appKey;
    private String appSecret;
    private String status;
    private LocalDateTime expireAt;
    private LocalDateTime lastUsedAt;
    private LocalDateTime createdAt;
}
