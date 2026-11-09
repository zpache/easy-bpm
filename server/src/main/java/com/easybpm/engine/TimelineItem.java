package com.easybpm.engine;

import java.time.LocalDateTime;

public record TimelineItem(
        String action,
        String nodeName,
        String operatorId,
        String operatorName,
        String comment,
        LocalDateTime createdAt
) {
}
