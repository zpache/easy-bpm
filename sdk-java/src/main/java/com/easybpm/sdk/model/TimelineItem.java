package com.easybpm.sdk.model;

/** 时间线条目。action: SUBMIT / APPROVE / REJECT / TRANSFER / REVOKE / RESUBMIT / CC。 */
public record TimelineItem(
        String action,
        String nodeName,
        String operatorId,
        String operatorName,
        String comment,
        String createdAt
) {
}
