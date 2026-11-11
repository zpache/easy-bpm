package com.easybpm.sdk.model;

import java.util.List;

/** 增量变更流。cursor 原样回传；hasMore=true 时表示仍有积压，SDK 会自动立即续拉。 */
public record ChangeFeed(
        long cursor,
        boolean hasMore,
        List<ChangeEvent> events,
        String serverTime
) {
}
