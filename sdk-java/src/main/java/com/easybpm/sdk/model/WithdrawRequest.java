package com.easybpm.sdk.model;

/** 发起人撤回运行中的审批。 */
public record WithdrawRequest(String userId, String comment) {
}
