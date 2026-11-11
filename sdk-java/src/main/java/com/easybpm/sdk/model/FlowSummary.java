package com.easybpm.sdk.model;

/** 已发布流程摘要。formSchema 为表单字段元数据（可为 null），用于接入方动态渲染发起表单。 */
public record FlowSummary(
        String code,
        String name,
        String category,
        String description,
        Integer version,
        Object formSchema
) {
}
