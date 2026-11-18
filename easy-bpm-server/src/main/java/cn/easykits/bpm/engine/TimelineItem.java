package cn.easykits.bpm.engine;

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
