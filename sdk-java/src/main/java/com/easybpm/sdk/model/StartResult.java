package com.easybpm.sdk.model;

import java.util.List;

/** 发起 / 重新提交结果。pendingTasks 为流转到当前节点的待办。 */
public record StartResult(
        String instanceId,
        String status,
        String currentNodeName,
        List<TaskView> pendingTasks
) {
}
