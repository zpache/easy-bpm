package com.easybpm.sdk.model;

/** 同意 / 驳回 / 撤回 / 转交的结果。 */
public record ActionResult(
        String instanceId,
        String status,
        String currentNodeName,
        String endedAt
) {
}
