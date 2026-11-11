package com.easybpm.sdk.model;

/**
 * 增量变更事件。
 *
 * <p>type：{@code TASK_CREATED}（新待办）、{@code TASK_COMPLETED}（已办）、
 * {@code CC_CREATED}（抄送）、{@code INSTANCE_UPDATED}（我发起的实例状态变化）。
 * eventId 跨轮询稳定，可直接用于幂等去重。
 */
public record ChangeEvent(
        String eventId,
        String type,
        String at,
        String taskId,
        String instanceId,
        String instanceTitle,
        String flowCode,
        String flowName,
        String nodeName,
        String action,
        String actorId,
        String actorName,
        String instanceStatus,
        String currentNodeName
) {
}
