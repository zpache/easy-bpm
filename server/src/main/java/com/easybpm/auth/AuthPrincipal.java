package com.easybpm.auth;

/**
 * 控制台登录态。externalUserId 是对接方组织里的人员编码，审批动作以它为准。
 */
public record AuthPrincipal(String consoleUserId, String tenantId, String displayName, String role,
                            String externalUserId) {

    public String approverId() {
        return externalUserId != null && !externalUserId.isBlank() ? externalUserId : consoleUserId;
    }

    public boolean isTenantAdmin() {
        return "TENANT_ADMIN".equals(role) || "PLATFORM_ADMIN".equals(role);
    }
}
