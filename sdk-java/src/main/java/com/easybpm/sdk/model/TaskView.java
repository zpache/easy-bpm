package com.easybpm.sdk.model;

/**
 * 待办 / 已办列表项。
 *
 * <p>时间字段为服务端格式化的字符串 {@code yyyy-MM-dd HH:mm:ss}，需要计算时再自行解析。
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
        String createdAt,
        String handledAt
) {
}
