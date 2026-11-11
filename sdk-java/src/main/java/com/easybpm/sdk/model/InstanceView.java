package com.easybpm.sdk.model;

/** 审批实例视图。status: RUNNING / APPROVED / REJECTED / CANCELED。 */
public record InstanceView(
        String instanceId,
        String flowCode,
        String flowName,
        Integer flowVersion,
        String businessKey,
        String title,
        String initiatorId,
        String initiatorName,
        String initiatorDeptCode,
        Object formData,
        String status,
        String currentNodeName,
        String startedAt,
        String endedAt
) {
}
