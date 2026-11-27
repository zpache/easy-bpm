package cn.easykits.bpm.engine;

import java.time.LocalDateTime;

public record InstanceView(
        String instanceId,
        String flowCode,
        String flowName,
        Integer flowVersion,
        String businessKey,
        String title,
        String initiatorId,
        String initiatorName,
        String initiatorDeptCode,
        Object formData,
        String status,
        String currentNodeName,
        String sourceAppKey,
        String sourceAppName,
        LocalDateTime startedAt,
        LocalDateTime endedAt
) {
}
