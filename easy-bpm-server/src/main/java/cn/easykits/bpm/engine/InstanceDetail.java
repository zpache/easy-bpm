package cn.easykits.bpm.engine;

import java.util.List;

public record InstanceDetail(InstanceView instance, List<TimelineItem> timeline, List<TaskView> pendingTasks,
                             Object formSchema) {
}
