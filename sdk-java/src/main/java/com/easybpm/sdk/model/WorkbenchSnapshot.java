package com.easybpm.sdk.model;

import java.util.List;

/**
 * 工作台快照：一次调用拿到角标、待办首页、未读抄送与增量游标。
 *
 * <p>{@code cursor} 可直接作为 {@code changes(userId, cursor, ...)} 的起点，避免回放历史事件。
 */
public record WorkbenchSnapshot(
        WorkbenchCounts counts,
        List<TaskView> todo,
        List<CcView> ccUnread,
        long cursor,
        String serverTime
) {
}
