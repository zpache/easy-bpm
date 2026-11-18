package com.easybpm.engine;

import java.time.LocalDateTime;

/**
 * 待办/已办列表项。instanceTitle、flowName 等来自实例投影，避免列表页 N+1 查询。
 */
public record TaskView(
        String taskId,
        String instanceId,
        String nodeId,
        String nodeName,
        String assigneeId,
        String assigneeName,
        String instanceTitle,
        String flowCode,
        String flowName,
        String initiatorName,
        String status,
        String action,
        LocalDateTime createdAt,
        LocalDateTime handledAt
) {
}
