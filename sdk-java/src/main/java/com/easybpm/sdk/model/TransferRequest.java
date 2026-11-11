package com.easybpm.sdk.model;

/** 转交待办给同租户在职人员。 */
public record TransferRequest(String userId, String targetUserId, String comment) {
}
