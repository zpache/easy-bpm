package com.easybpm.sdk.model;

import java.util.List;

/** 审批详情：实例 + 时间线 + 当前待办 + 表单 schema（可为 null）。 */
public record InstanceDetail(
        InstanceView instance,
        List<TimelineItem> timeline,
        List<TaskView> pendingTasks,
        Object formSchema
) {
}
