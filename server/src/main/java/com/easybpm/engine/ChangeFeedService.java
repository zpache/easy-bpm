package com.easybpm.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.easybpm.common.BizException;
import com.easybpm.org.OrgService;
import com.easybpm.tenant.TenantContext;
import org.flowable.engine.HistoryService;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 轮询式变更流：接入方不接 Webhook，也能「近实时」感知待办与结果。
 *
 * <p>事件来源全部是已有数据，不额外写事件表：
 * <ul>
 *   <li>{@code TASK_CREATED} —— Flowable 历史任务（createTime 落在区间内，处理人=本人）；</li>
 *   <li>{@code TASK_COMPLETED} —— Flowable 历史任务（endTime 落在区间内，处理人=本人）；</li>
 *   <li>{@code CC_CREATED} —— {@code bpm_cc} 新增抄送；</li>
 *   <li>{@code INSTANCE_UPDATED} —— 本人发起的实例状态/节点变化（{@code updated_at}）。</li>
 * </ul>
 *
 * <p>游标是毫秒时间戳。无积压时服务端返回的游标会回退 {@link #OVERLAP_MS} 形成重叠窗口，
 * 规避「事件已写但事务尚未提交」的可见性缺口；因此同一条事件可能被重复下发，
 * 接入方按 {@code eventId} 去重即为「至少一次」语义。有积压（{@code hasMore=true}）时
 * 游标停在已下发事件的最后时间，保证推进、不会重复回放整页。
 */
@Service
public class ChangeFeedService {

    /** 默认返回条数。 */
    private static final int DEFAULT_LIMIT = 50;
    /** 单次上限，避免一次轮询拖垮数据库。 */
    private static final int MAX_LIMIT = 200;
    /** 游标重叠窗口：覆盖事务提交延迟与同毫秒并发。 */
    private static final long OVERLAP_MS = 3000;

    private final HistoryService historyService;
    private final BpmInstanceMapper instanceMapper;
    private final BpmCcMapper ccMapper;
    private final BpmTaskRecordMapper recordMapper;
    private final OrgService orgService;

    public ChangeFeedService(HistoryService historyService, BpmInstanceMapper instanceMapper,
                             BpmCcMapper ccMapper, BpmTaskRecordMapper recordMapper,
                             OrgService orgService) {
        this.historyService = historyService;
        this.instanceMapper = instanceMapper;
        this.ccMapper = ccMapper;
        this.recordMapper = recordMapper;
        this.orgService = orgService;
    }

    /**
     * 拉取增量事件。
     *
     * @param userId 组织人员编码（接入方自己的用户 ID）
     * @param cursor 上一轮的 {@code cursor}；为 null 表示初始化，只返回游标不回放历史
     * @param limit  本页最多事件数
     */
    public ChangeFeed feed(String userId, Long cursor, Integer limit) {
        String tenantId = TenantContext.require();
        if (userId == null || userId.isBlank()) {
            throw BizException.badRequest("userId 不能为空");
        }
        int size = limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        LocalDateTime serverTime = LocalDateTime.now();
        if (cursor == null || cursor <= 0) {
            // 初始化：接入方首次接入不回放历史事件，用 /workbench 拿当前快照，再用本游标增量跟进
            return new ChangeFeed(serverTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    false, List.of(), serverTime);
        }

        Date since = new Date(cursor);
        LocalDateTime sinceLocal = LocalDateTime.ofInstant(since.toInstant(), ZoneId.systemDefault());

        List<ChangeEvent> events = new ArrayList<>();
        events.addAll(taskCreatedEvents(tenantId, userId, since, size + 1, serverTime));
        events.addAll(taskCompletedEvents(tenantId, userId, since, size + 1, serverTime));
        events.addAll(ccEvents(userId, sinceLocal, size + 1));
        events.addAll(instanceEvents(userId, sinceLocal, size + 1));

        List<ChangeEvent> merged = dedupeAndSort(events);
        boolean hasMore = merged.size() > size;
        List<ChangeEvent> page = hasMore ? merged.subList(0, size) : merged;

        long nextCursor;
        if (hasMore && !page.isEmpty()) {
            // 有积压：游标停在最后一条事件的时间上，并强制前进，避免同一毫秒内超量事件导致原地打转
            long lastEventMillis = toMillis(page.get(page.size() - 1).at());
            nextCursor = Math.max(lastEventMillis, cursor + 1);
        } else {
            // 无积压：回退重叠窗口，下一轮会重发最近 3 秒的事件，由 eventId 去重
            nextCursor = toMillis(serverTime) - OVERLAP_MS;
        }
        return new ChangeFeed(nextCursor, hasMore, List.copyOf(page), serverTime);
    }

    private List<ChangeEvent> taskCreatedEvents(String tenantId, String userId, Date since, int fetch,
                                                LocalDateTime serverTime) {
        List<HistoricTaskInstance> tasks = historyService.createHistoricTaskInstanceQuery()
                .taskTenantId(tenantId)
                .taskAssignee(userId)
                .taskCreatedAfter(since)
                .orderByTaskCreateTime().asc()
                .listPage(0, fetch);
        return taskEvents(tasks, "TASK_CREATED", true, serverTime);
    }

    private List<ChangeEvent> taskCompletedEvents(String tenantId, String userId, Date since, int fetch,
                                                  LocalDateTime serverTime) {
        List<HistoricTaskInstance> tasks = historyService.createHistoricTaskInstanceQuery()
                .taskTenantId(tenantId)
                .taskAssignee(userId)
                .finished()
                .taskCompletedAfter(since)
                .orderByHistoricTaskInstanceEndTime().asc()
                .listPage(0, fetch);
        return taskEvents(tasks, "TASK_COMPLETED", false, serverTime);
    }

    private List<ChangeEvent> taskEvents(List<HistoricTaskInstance> tasks, String type, boolean created,
                                         LocalDateTime serverTime) {
        if (tasks.isEmpty()) {
            return List.of();
        }
        List<String> engineIds = tasks.stream()
                .map(HistoricTaskInstance::getProcessInstanceId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<String, BpmInstance> instances = instancesByEngine(engineIds);
        Map<String, String> actions = created ? Map.of() : actionsOf(
                tasks.stream().map(HistoricTaskInstance::getId).toList());
        List<String> assignees = tasks.stream()
                .map(HistoricTaskInstance::getAssignee).filter(Objects::nonNull).distinct().toList();
        Map<String, String> names = orgService.namesOf(assignees);

        List<ChangeEvent> result = new ArrayList<>();
        for (HistoricTaskInstance task : tasks) {
            BpmInstance instance = instances.get(task.getProcessInstanceId());
            if (instance == null) {
                // 实例投影已被清理的残留任务不下发，避免接入方拿到空标题的幽灵待办
                continue;
            }
            LocalDateTime at = created ? toLocal(task.getCreateTime()) : toLocal(task.getEndTime());
            result.add(new ChangeEvent(
                    type + ":" + task.getId(),
                    type,
                    at == null ? serverTime : at,
                    task.getId(),
                    instance.getInstanceId(),
                    instance.getTitle(),
                    instance.getFlowCode(),
                    instance.getFlowName(),
                    task.getName(),
                    actions.get(task.getId()),
                    task.getAssignee(),
                    names.getOrDefault(task.getAssignee(), task.getAssignee()),
                    instance.getStatus(),
                    instance.getCurrentNodeName()));
        }
        return result;
    }

    private List<ChangeEvent> ccEvents(String userId, LocalDateTime since, int fetch) {
        List<BpmCc> rows = ccMapper.selectList(new LambdaQueryWrapper<BpmCc>()
                .eq(BpmCc::getUserId, userId)
                .gt(BpmCc::getCreatedAt, since)
                .orderByAsc(BpmCc::getCreatedAt)
                .last("limit " + fetch));
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<String, BpmInstance> instances = instancesByExternal(rows.stream()
                .map(BpmCc::getInstanceId).distinct().toList());
        return rows.stream().map(cc -> {
            BpmInstance instance = instances.get(cc.getInstanceId());
            return new ChangeEvent(
                    "CC:" + cc.getId(),
                    "CC_CREATED",
                    cc.getCreatedAt(),
                    null,
                    cc.getInstanceId(),
                    instance == null ? null : instance.getTitle(),
                    instance == null ? null : instance.getFlowCode(),
                    instance == null ? null : instance.getFlowName(),
                    cc.getNodeName(),
                    null,
                    cc.getUserId(),
                    cc.getUserName(),
                    instance == null ? null : instance.getStatus(),
                    instance == null ? null : instance.getCurrentNodeName());
        }).toList();
    }

    private List<ChangeEvent> instanceEvents(String userId, LocalDateTime since, int fetch) {
        List<BpmInstance> rows = instanceMapper.selectList(new LambdaQueryWrapper<BpmInstance>()
                .eq(BpmInstance::getInitiatorId, userId)
                .gt(BpmInstance::getUpdatedAt, since)
                .orderByAsc(BpmInstance::getUpdatedAt)
                .last("limit " + fetch));
        return rows.stream().map(row -> new ChangeEvent(
                "INSTANCE:" + row.getInstanceId() + ":" + toMillis(row.getUpdatedAt()) + ":" + row.getStatus(),
                "INSTANCE_UPDATED",
                row.getUpdatedAt(),
                null,
                row.getInstanceId(),
                row.getTitle(),
                row.getFlowCode(),
                row.getFlowName(),
                row.getCurrentNodeName(),
                null,
                row.getInitiatorId(),
                row.getInitiatorName(),
                row.getStatus(),
                row.getCurrentNodeName())).toList();
    }

    private List<ChangeEvent> dedupeAndSort(List<ChangeEvent> events) {
        Map<String, ChangeEvent> byId = new LinkedHashMap<>();
        for (ChangeEvent event : events) {
            byId.putIfAbsent(event.eventId(), event);
        }
        List<ChangeEvent> sorted = new ArrayList<>(byId.values());
        sorted.sort(Comparator
                .comparing(ChangeEvent::at, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ChangeEvent::eventId));
        return sorted;
    }

    private Map<String, BpmInstance> instancesByEngine(List<String> engineIds) {
        if (engineIds.isEmpty()) {
            return Map.of();
        }
        Map<String, BpmInstance> result = new LinkedHashMap<>();
        instanceMapper.selectList(new LambdaQueryWrapper<BpmInstance>()
                        .in(BpmInstance::getEngineInstanceId, engineIds))
                .forEach(i -> result.put(i.getEngineInstanceId(), i));
        return result;
    }

    private Map<String, BpmInstance> instancesByExternal(List<String> instanceIds) {
        if (instanceIds.isEmpty()) {
            return Map.of();
        }
        Map<String, BpmInstance> result = new LinkedHashMap<>();
        instanceMapper.selectList(new LambdaQueryWrapper<BpmInstance>()
                        .in(BpmInstance::getInstanceId, new LinkedHashSet<>(instanceIds)))
                .forEach(i -> result.put(i.getInstanceId(), i));
        return result;
    }

    private Map<String, String> actionsOf(List<String> taskIds) {
        List<String> valid = taskIds.stream().filter(Objects::nonNull).distinct().toList();
        if (valid.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        recordMapper.selectList(new LambdaQueryWrapper<BpmTaskRecord>()
                        .in(BpmTaskRecord::getTaskId, valid))
                .forEach(r -> result.putIfAbsent(r.getTaskId(), r.getAction()));
        return result;
    }

    private LocalDateTime toLocal(Date date) {
        return date == null ? null : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    private long toMillis(LocalDateTime time) {
        return time == null ? 0 : time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
