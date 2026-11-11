package com.easybpm.engine;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 增量变更流。
 *
 * @param cursor     下一次轮询要回传的游标（毫秒时间戳；已含 3 秒重叠窗口，用于对齐提交可见性）
 * @param hasMore    本页事件达到 limit，仍有积压，建议立即用本 cursor 再拉一次
 * @param events     按发生时间升序的事件列表
 * @param serverTime 服务端当前时间，便于客户端校准时钟
 */
public record ChangeFeed(long cursor, boolean hasMore, List<ChangeEvent> events, LocalDateTime serverTime) {
}
