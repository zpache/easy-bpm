package com.easybpm.engine;

import java.time.LocalDateTime;

/**
 * 抄送列表项。实例标题、流程名、发起人等展示字段来自实例投影，避免列表页 N+1 查询。
 */
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
        LocalDateTime createdAt
) {
}
