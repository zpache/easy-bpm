package com.easybpm.engine;

import java.time.LocalDateTime;

/**
 * 轮询变更事件。接入方按 {@code eventId} 去重即可获得「至少一次」语义，
 * 无需回调地址，也不需要自己维护待办快照。
 *
 * @param eventId         事件唯一标识，跨轮询稳定（同一事件重复下发时不变）
 * @param type            TASK_CREATED / TASK_COMPLETED / CC_CREATED / INSTANCE_UPDATED
 * @param at              事件发生时间
 * @param taskId          任务 ID（任务类事件）
 * @param instanceId      对外实例 ID
 * @param instanceTitle   审批标题
 * @param flowCode        流程编码
 * @param flowName        流程名称
 * @param nodeName        节点名称
 * @param action          已办动作：APPROVE / REJECT / TRANSFER 等
 * @param actorId         事件相关人（任务处理人）
 * @param actorName       事件相关人姓名
 * @param instanceStatus  实例状态
 * @param currentNodeName 实例当前节点
 */
public record ChangeEvent(
        String eventId,
        String type,
        LocalDateTime at,
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
