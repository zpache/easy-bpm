package com.easybpm.sdk.model;

/** 抄送我的列表项。read=true 表示已读。 */
public record CcView(
        String id,
        String instanceId,
        String nodeId,
        String nodeName,
        String instanceTitle,
        String flowCode,
        String flowName,
        String initiatorName,
        String instanceStatus,
        boolean read,
        String createdAt
) {
}
