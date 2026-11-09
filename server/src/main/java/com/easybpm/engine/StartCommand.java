package com.easybpm.engine;

import java.util.List;
import java.util.Map;

/**
 * 发起审批的入参。OpenAPI 与控制器都收敛到这里，避免两套语义。
 */
public record StartCommand(
        String flowCode,
        String businessKey,
        String title,
        String initiatorId,
        String initiatorName,
        String initiatorDeptCode,
        Map<String, Object> formData,
        List<String> ccUsers,
        String callbackUrl,
        String idempotencyKey
) {
    public StartCommand {
        formData = formData == null ? Map.of() : formData;
        ccUsers = ccUsers == null ? List.of() : ccUsers;
    }
}
