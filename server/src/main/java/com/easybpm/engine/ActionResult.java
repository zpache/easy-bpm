package com.easybpm.engine;

import java.time.LocalDateTime;

/**
 * 审批动作结果。实例结束时携带 endedAt，方便接入方一步拿到结束时间。
 */
public record ActionResult(String instanceId, String status, String currentNodeName, LocalDateTime endedAt) {
}
