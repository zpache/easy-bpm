package com.easybpm.engine;

import java.util.List;

public record StartResult(String instanceId, String status, String currentNodeName, List<TaskView> pendingTasks) {
}
