package cn.easykits.bpm.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.easykits.bpm.common.BizException;
import cn.easykits.bpm.common.ErrorCode;
import cn.easykits.bpm.common.JsonCodec;
import cn.easykits.bpm.common.PageResult;
import cn.easykits.bpm.flow.BpmnCompiler;
import cn.easykits.bpm.flow.FlowDefinition;
import cn.easykits.bpm.flow.FlowDefinitionMapper;
import cn.easykits.bpm.org.OrgService;
import cn.easykits.bpm.tenant.TenantContext;
import org.flowable.engine.HistoryService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 工作台查询：待办、已办、已提交、详情与数量统计。
 *
 * <p>待办/已办来自 Flowable 实时数据，标题、发起人等展示字段来自实例投影，
 * 通过 instanceId 批量补齐，避免列表页 N+1。
 */
@Service
public class WorkbenchQueryService {

    private static final int MAX_KEYWORD_HITS = 200;

    private final TaskService taskService;
    private final HistoryService historyService;
    private final BpmInstanceMapper instanceMapper;
    private final BpmTaskRecordMapper recordMapper;
    private final BpmCcMapper ccMapper;
    private final JsonCodec json;
    private final OrgService orgService;
    private final BpmnCompiler compiler;
    private final FlowDefinitionMapper flowMapper;

    public WorkbenchQueryService(TaskService taskService, HistoryService historyService,
                                 BpmInstanceMapper instanceMapper, BpmTaskRecordMapper recordMapper,
                                 BpmCcMapper ccMapper, JsonCodec json, OrgService orgService,
                                 BpmnCompiler compiler, FlowDefinitionMapper flowMapper) {
        this.taskService = taskService;
        this.historyService = historyService;
        this.instanceMapper = instanceMapper;
        this.recordMapper = recordMapper;
        this.ccMapper = ccMapper;
        this.json = json;
        this.orgService = orgService;
        this.compiler = compiler;
        this.flowMapper = flowMapper;
    }

    public PageResult<TaskView> todo(String userId, int page, int size, String flowCode, String keyword) {
        return todo(userId, page, size, flowCode, keyword, false, null);
    }

    /** 接入方视角：可按来源 appKey 过滤；appKey 为空表示本租户全部。 */
    public PageResult<TaskView> todo(String userId, int page, int size, String flowCode, String keyword,
                                     String appKey) {
        return todo(userId, page, size, flowCode, keyword, false, appKey);
    }

    /**
     * 管理视角：本租户全部在办任务，不按办理人过滤，仅按租户隔离。
     * 与 OpenAPI 的个人待办共用同一查询与投影逻辑。
     */
    public PageResult<TaskView> todoByTenant(int page, int size, String flowCode, String keyword) {
        return todo(null, page, size, flowCode, keyword, true, null);
    }

    /** allTenant=true 表示不限办理人（管理视角），否则只取 assignee 的任务。 */
    private PageResult<TaskView> todo(String userId, int page, int size, String flowCode, String keyword,
                                      boolean allTenant, String appKey) {
        String tenantId = TenantContext.require();
        TaskQuery query = taskService.createTaskQuery().taskTenantId(tenantId);
        if (!allTenant && userId != null && !userId.isBlank()) {
            query.taskAssignee(userId);
        }
        if (appKey != null && !appKey.isBlank()) {
            query.processVariableValueEquals(FlowEngineService.SOURCE_APP_KEY_VAR, appKey);
        }
        if (flowCode != null && !flowCode.isBlank()) {
            query.processDefinitionKey(compiler.processKeyOf(flowCode));
        }
        if (keyword != null && !keyword.isBlank()) {
            List<String> ids = matchingInstanceIds(keyword, flowCode);
            if (ids.isEmpty()) {
                return PageResult.empty(page, size);
            }
            query.processInstanceIdIn(ids);
        }

        long total = query.count();
        List<Task> tasks = query.orderByTaskCreateTime().desc()
                .listPage(offset(page, size), size);
        if (tasks.isEmpty()) {
            return PageResult.of(List.of(), total, page, size);
        }

        Map<String, BpmInstance> instances = instanceMap(tasks.stream().map(Task::getProcessInstanceId).toList());
        Map<String, String> names = namesOf(tasks.stream().map(Task::getAssignee).toList());
        List<TaskView> views = tasks.stream()
                .map(task -> toTaskView(task, instances.get(task.getProcessInstanceId()), names, null))
                .toList();
        return PageResult.of(views, total, page, size);
    }

    public PageResult<TaskView> done(String userId, int page, int size, String flowCode, String keyword) {
        return done(userId, page, size, flowCode, keyword, false, null);
    }

    /** 接入方视角：可按来源 appKey 过滤；appKey 为空表示本租户全部。 */
    public PageResult<TaskView> done(String userId, int page, int size, String flowCode, String keyword,
                                     String appKey) {
        return done(userId, page, size, flowCode, keyword, false, appKey);
    }

    /** 管理视角：本租户全部已办任务，不按办理人过滤，仅按租户隔离。 */
    public PageResult<TaskView> doneByTenant(int page, int size, String flowCode, String keyword) {
        return done(null, page, size, flowCode, keyword, true, null);
    }

    /** allTenant=true 表示不限办理人（管理视角），否则只取 assignee 的历史任务。 */
    private PageResult<TaskView> done(String userId, int page, int size, String flowCode, String keyword,
                                      boolean allTenant, String appKey) {
        String tenantId = TenantContext.require();
        HistoricTaskInstanceQuery query = historyService.createHistoricTaskInstanceQuery()
                .taskTenantId(tenantId).finished();
        if (!allTenant && userId != null && !userId.isBlank()) {
            query.taskAssignee(userId);
        }
        if (appKey != null && !appKey.isBlank()) {
            query.processVariableValueEquals(FlowEngineService.SOURCE_APP_KEY_VAR, appKey);
        }
        if (flowCode != null && !flowCode.isBlank()) {
            query.processDefinitionKey(compiler.processKeyOf(flowCode));
        }
        if (keyword != null && !keyword.isBlank()) {
            List<String> ids = matchingInstanceIds(keyword, flowCode);
            if (ids.isEmpty()) {
                return PageResult.empty(page, size);
            }
            query.processInstanceIdIn(ids);
        }

        long total = query.count();
        List<HistoricTaskInstance> tasks = query.orderByHistoricTaskInstanceEndTime().desc()
                .listPage(offset(page, size), size);
        if (tasks.isEmpty()) {
            return PageResult.of(List.of(), total, page, size);
        }

        Map<String, BpmInstance> instances = instanceMap(tasks.stream()
                .map(HistoricTaskInstance::getProcessInstanceId).toList());
        // 实例投影已被删除的历史任务（如测试数据清理后的残留）不展示，避免出现空标题幽灵行
        List<HistoricTaskInstance> visible = tasks.stream()
                .filter(task -> instances.containsKey(task.getProcessInstanceId()))
                .toList();
        if (visible.size() < tasks.size()) {
            total = Math.max(total - countOrphanInstances(query), visible.size());
            tasks = visible;
            if (tasks.isEmpty()) {
                return PageResult.of(List.of(), total, page, size);
            }
        }
        Map<String, String> names = namesOf(tasks.stream().map(HistoricTaskInstance::getAssignee).toList());
        Map<String, String> actions = actionsOf(tasks.stream().map(HistoricTaskInstance::getId).toList());

        List<TaskView> views = tasks.stream()
                .map(task -> new TaskView(
                        task.getId(),
                        task.getProcessInstanceId(),
                        task.getTaskDefinitionKey(),
                        task.getName(),
                        task.getAssignee(),
                        names.getOrDefault(task.getAssignee(), task.getAssignee()),
                        titleOf(instances.get(task.getProcessInstanceId())),
                        flowCodeOf(instances.get(task.getProcessInstanceId())),
                        flowNameOf(instances.get(task.getProcessInstanceId())),
                        initiatorOf(instances.get(task.getProcessInstanceId())),
                        sourceAppKeyOf(instances.get(task.getProcessInstanceId())),
                        sourceAppNameOf(instances.get(task.getProcessInstanceId())),
                        "FINISHED",
                        actions.get(task.getId()),
                        toLocal(task.getCreateTime()),
                        toLocal(task.getEndTime())))
                .toList();
        return PageResult.of(views, total, page, size);
    }

    public PageResult<InstanceView> submitted(String userId, int page, int size, String flowCode,
                                              String status, String keyword) {
        return submitted(userId, page, size, flowCode, status, keyword, null);
    }

    /** 接入方视角：可按来源 appKey 过滤（查询投影表 source_app_key 列）。 */
    public PageResult<InstanceView> submitted(String userId, int page, int size, String flowCode,
                                              String status, String keyword, String appKey) {
        LambdaQueryWrapper<BpmInstance> wrapper = new LambdaQueryWrapper<BpmInstance>()
                .eq(BpmInstance::getInitiatorId, userId)
                .eq(appKey != null && !appKey.isBlank(), BpmInstance::getSourceAppKey, appKey)
                .eq(flowCode != null && !flowCode.isBlank(), BpmInstance::getFlowCode, flowCode)
                .eq(status != null && !status.isBlank(), BpmInstance::getStatus, status)
                .like(keyword != null && !keyword.isBlank(), BpmInstance::getTitle, keyword)
                .orderByDesc(BpmInstance::getStartedAt);
        Page<BpmInstance> result = instanceMapper.selectPage(new Page<>(page, size), wrapper);
        List<InstanceView> views = result.getRecords().stream().map(this::toInstanceView).toList();
        return PageResult.of(views, result.getTotal(), page, size);
    }

    /**
     * 管理员视角：本租户全部实例分页查询，支持流程/状态过滤，关键字匹配标题或发起人姓名。
     * 调用方须自行校验管理员权限。
     */
    public PageResult<InstanceView> allInstances(int page, int size, String flowCode, String status,
                                                 String keyword) {
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        LambdaQueryWrapper<BpmInstance> wrapper = new LambdaQueryWrapper<BpmInstance>()
                .eq(flowCode != null && !flowCode.isBlank(), BpmInstance::getFlowCode, flowCode)
                .eq(status != null && !status.isBlank(), BpmInstance::getStatus, status)
                .and(hasKeyword, w -> w.like(BpmInstance::getTitle, keyword)
                        .or().like(BpmInstance::getInitiatorName, keyword))
                .orderByDesc(BpmInstance::getStartedAt);
        Page<BpmInstance> result = instanceMapper.selectPage(new Page<>(page, size), wrapper);
        List<InstanceView> views = result.getRecords().stream().map(this::toInstanceView).toList();
        return PageResult.of(views, result.getTotal(), page, size);
    }

    public WorkbenchCounts counts(String userId) {
        return counts(userId, null);
    }

    /** 接入方视角：可按来源 appKey 过滤四类计数；appKey 为空表示本租户全部。 */
    public WorkbenchCounts counts(String userId, String appKey) {
        String tenantId = TenantContext.require();
        boolean byApp = appKey != null && !appKey.isBlank();
        var todoQuery = taskService.createTaskQuery().taskTenantId(tenantId).taskAssignee(userId);
        var doneQuery = historyService.createHistoricTaskInstanceQuery()
                .taskTenantId(tenantId).taskAssignee(userId).finished();
        if (byApp) {
            todoQuery.processVariableValueEquals(FlowEngineService.SOURCE_APP_KEY_VAR, appKey);
            doneQuery.processVariableValueEquals(FlowEngineService.SOURCE_APP_KEY_VAR, appKey);
        }
        long todo = todoQuery.count();
        long done = doneQuery.count();
        long submitted = instanceMapper.selectCount(new LambdaQueryWrapper<BpmInstance>()
                .eq(BpmInstance::getInitiatorId, userId)
                .eq(byApp, BpmInstance::getSourceAppKey, appKey));
        var ccWrapper = new LambdaQueryWrapper<BpmCc>()
                .eq(BpmCc::getUserId, userId)
                .eq(BpmCc::getReadFlag, 0);
        if (byApp) {
            List<String> appInstanceIds = instanceMapper.selectList(new LambdaQueryWrapper<BpmInstance>()
                            .eq(BpmInstance::getSourceAppKey, appKey))
                    .stream().map(BpmInstance::getInstanceId).toList();
            if (appInstanceIds.isEmpty()) {
                return new WorkbenchCounts(todo, done, submitted, 0);
            }
            ccWrapper.in(BpmCc::getInstanceId, appInstanceIds);
        }
        long ccUnread = ccMapper.selectCount(ccWrapper);
        return new WorkbenchCounts(todo, done, submitted, ccUnread);
    }

    /** 管理视角：本租户全量的待办/已办/发起/未读抄送统计，不按个人或租户外过滤。 */
    public WorkbenchCounts countsByTenant() {
        String tenantId = TenantContext.require();
        long todo = taskService.createTaskQuery().taskTenantId(tenantId).count();
        long done = historyService.createHistoricTaskInstanceQuery()
                .taskTenantId(tenantId).finished().count();
        long submitted = instanceMapper.selectCount(null);
        long ccUnread = ccMapper.selectCount(new LambdaQueryWrapper<BpmCc>()
                .eq(BpmCc::getReadFlag, 0));
        return new WorkbenchCounts(todo, done, submitted, ccUnread);
    }

    /**
     * 管理视角：本租户全部抄送记录，不按接收人过滤，仅按租户隔离。
     */
    public PageResult<CcView> ccByTenant(int page, int size, boolean unreadOnly, String keyword) {
        return cc(null, page, size, unreadOnly, keyword, true, null);
    }

    /**
     * 抄送我的：按接收人分页查询，可按关键字（实例标题）与未读过滤。
     */
    public PageResult<CcView> cc(String userId, int page, int size, boolean unreadOnly, String keyword) {
        return cc(userId, page, size, unreadOnly, keyword, false, null);
    }

    /** 接入方视角：可按来源 appKey 过滤（关联实例投影表）。 */
    public PageResult<CcView> cc(String userId, int page, int size, boolean unreadOnly, String keyword,
                                 String appKey) {
        return cc(userId, page, size, unreadOnly, keyword, false, appKey);
    }

    /** allTenant=true 表示不限接收人（管理视角），否则只取该接收人的抄送。 */
    private PageResult<CcView> cc(String userId, int page, int size, boolean unreadOnly, String keyword,
                                  boolean allTenant, String appKey) {
        LambdaQueryWrapper<BpmCc> wrapper = new LambdaQueryWrapper<BpmCc>()
                .eq(!allTenant && userId != null && !userId.isBlank(), BpmCc::getUserId, userId)
                .eq(unreadOnly, BpmCc::getReadFlag, 0)
                .orderByDesc(BpmCc::getCreatedAt);
        boolean byApp = appKey != null && !appKey.isBlank();
        boolean byKeyword = keyword != null && !keyword.isBlank();
        if (byApp || byKeyword) {
            var instanceQuery = new LambdaQueryWrapper<BpmInstance>()
                    .eq(byApp, BpmInstance::getSourceAppKey, appKey)
                    .like(byKeyword, BpmInstance::getTitle, keyword);
            if (byKeyword) {
                instanceQuery.last("limit " + MAX_KEYWORD_HITS);
            }
            List<String> instanceIds = instanceMapper.selectList(instanceQuery)
                    .stream().map(BpmInstance::getInstanceId).toList();
            if (instanceIds.isEmpty()) {
                return PageResult.empty(page, size);
            }
            wrapper.in(BpmCc::getInstanceId, instanceIds);
        }
        Page<BpmCc> result = ccMapper.selectPage(new Page<>(page, size), wrapper);
        if (result.getRecords().isEmpty()) {
            return PageResult.of(List.of(), result.getTotal(), page, size);
        }
        Map<String, BpmInstance> instances = instanceByExternalId(
                result.getRecords().stream().map(BpmCc::getInstanceId).toList());
        List<CcView> views = result.getRecords().stream()
                .map(cc -> toCcView(cc, instances.get(cc.getInstanceId())))
                .toList();
        return PageResult.of(views, result.getTotal(), page, size);
    }

    /**
     * 标记抄送已读。只允许本人操作自己的抄送记录，重复标记幂等。
     */
    public void markCcRead(String id, String userId) {
        BpmCc cc = requireCc(id);
        if (!Objects.equals(cc.getUserId(), userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能标记自己的抄送记录");
        }
        markRead(cc);
    }

    /** 管理视角：标记本租户任意抄送已读，不校验接收人（租户插件保证跨租户隔离）。 */
    public void markCcReadByTenant(String id) {
        markRead(requireCc(id));
    }

    private BpmCc requireCc(String id) {
        BpmCc cc = ccMapper.selectById(id);
        if (cc == null) {
            throw new BizException(ErrorCode.INSTANCE_NOT_FOUND, "抄送记录不存在：" + id);
        }
        return cc;
    }

    private void markRead(BpmCc cc) {
        if (cc.getReadFlag() != null && cc.getReadFlag() == 1) {
            return;
        }
        cc.setReadFlag(1);
        ccMapper.updateById(cc);
    }

    private CcView toCcView(BpmCc cc, BpmInstance instance) {
        return new CcView(cc.getId(), cc.getInstanceId(), cc.getNodeId(), cc.getNodeName(),
                titleOf(instance), flowCodeOf(instance), flowNameOf(instance), initiatorOf(instance),
                sourceAppKeyOf(instance), sourceAppNameOf(instance),
                instance == null ? null : instance.getStatus(),
                cc.getReadFlag() != null && cc.getReadFlag() == 1,
                cc.getCreatedAt());
    }

    private Map<String, BpmInstance> instanceByExternalId(Collection<String> instanceIds) {
        if (instanceIds == null || instanceIds.isEmpty()) {
            return Map.of();
        }
        Map<String, BpmInstance> result = new LinkedHashMap<>();
        instanceMapper.selectList(new LambdaQueryWrapper<BpmInstance>()
                        .in(BpmInstance::getInstanceId, instanceIds))
                .forEach(i -> result.put(i.getInstanceId(), i));
        return result;
    }

    public InstanceDetail detail(String instanceId) {
        BpmInstance instance = instanceMapper.selectOne(new LambdaQueryWrapper<BpmInstance>()
                .eq(BpmInstance::getInstanceId, instanceId));
        if (instance == null) {
            throw new BizException(ErrorCode.INSTANCE_NOT_FOUND, "审批实例不存在：" + instanceId);
        }
        List<BpmTaskRecord> records = recordMapper.selectList(new LambdaQueryWrapper<BpmTaskRecord>()
                .eq(BpmTaskRecord::getInstanceId, instanceId)
                .orderByAsc(BpmTaskRecord::getCreatedAt));
        List<TimelineItem> timeline = records.stream()
                .map(r -> new TimelineItem(r.getAction(), r.getNodeName(), r.getOperatorId(),
                        r.getOperatorName(), r.getComment(), r.getCreatedAt()))
                .toList();

        List<Task> active = taskService.createTaskQuery()
                .processInstanceId(instance.getEngineInstanceId())
                .orderByTaskCreateTime().asc().list();
        Map<String, String> names = namesOf(active.stream().map(Task::getAssignee).toList());
        List<TaskView> pending = active.stream()
                .map(t -> toTaskView(t, instance, names, null))
                .toList();

        // 表单字段元数据（可选），用于详情页可读渲染；schema 为空时前端降级展示原始 JSON
        FlowDefinition flow = flowMapper.selectOne(new LambdaQueryWrapper<FlowDefinition>()
                .eq(FlowDefinition::getCode, instance.getFlowCode()));
        Object formSchema = flow == null || flow.getFormSchema() == null
                ? null : json.readMap(flow.getFormSchema());
        return new InstanceDetail(toInstanceView(instance), timeline, pending, formSchema);
    }

    public InstanceView requireView(String instanceId) {
        return detail(instanceId).instance();
    }

    /**
     * 按接入方业务主键反查实例。业务主键在同一流程内通常唯一，跨流程可能重复，
     * 因此按 startedAt 倒序取最近一条，可用 flowCode 精确限定。
     */
    public InstanceView byBusinessKey(String businessKey, String flowCode) {
        return byBusinessKey(businessKey, flowCode, null);
    }

    /** 接入方视角：可按来源 appKey 过滤，只反查本应用发起的实例。 */
    public InstanceView byBusinessKey(String businessKey, String flowCode, String appKey) {
        BpmInstance row = instanceMapper.selectOne(new LambdaQueryWrapper<BpmInstance>()
                .eq(BpmInstance::getBusinessKey, businessKey)
                .eq(flowCode != null && !flowCode.isBlank(), BpmInstance::getFlowCode, flowCode)
                .eq(appKey != null && !appKey.isBlank(), BpmInstance::getSourceAppKey, appKey)
                .orderByDesc(BpmInstance::getStartedAt)
                .last("limit 1"));
        if (row == null) {
            throw new BizException(ErrorCode.INSTANCE_NOT_FOUND,
                    "未找到业务主键对应的审批：" + businessKey);
        }
        return toInstanceView(row);
    }

    public List<InstanceView> recent(int limit) {
        List<BpmInstance> rows = instanceMapper.selectList(new LambdaQueryWrapper<BpmInstance>()
                .orderByDesc(BpmInstance::getStartedAt).last("limit " + Math.max(1, limit)));
        return rows.stream().map(this::toInstanceView).toList();
    }

    private TaskView toTaskView(Task task, BpmInstance instance, Map<String, String> names, String action) {
        return new TaskView(task.getId(), task.getProcessInstanceId(), task.getTaskDefinitionKey(),
                task.getName(), task.getAssignee(),
                names.getOrDefault(task.getAssignee(), task.getAssignee()),
                titleOf(instance), flowCodeOf(instance), flowNameOf(instance), initiatorOf(instance),
                sourceAppKeyOf(instance), sourceAppNameOf(instance),
                "PENDING", action, toLocal(task.getCreateTime()), null);
    }

    private InstanceView toInstanceView(BpmInstance instance) {
        return new InstanceView(instance.getInstanceId(), instance.getFlowCode(), instance.getFlowName(),
                instance.getFlowVersion(), instance.getBusinessKey(), instance.getTitle(),
                instance.getInitiatorId(), instance.getInitiatorName(), instance.getInitiatorDeptCode(),
                json.readMap(instance.getFormData()), instance.getStatus(), instance.getCurrentNodeName(),
                instance.getSourceAppKey(), instance.getSourceAppName(),
                instance.getStartedAt(), instance.getEndedAt());
    }

    private Map<String, BpmInstance> instanceMap(Collection<String> engineInstanceIds) {
        if (engineInstanceIds == null || engineInstanceIds.isEmpty()) {
            return Map.of();
        }
        Map<String, BpmInstance> result = new LinkedHashMap<>();
        instanceMapper.selectList(new LambdaQueryWrapper<BpmInstance>()
                        .in(BpmInstance::getEngineInstanceId, engineInstanceIds))
                .forEach(i -> result.put(i.getEngineInstanceId(), i));
        return result;
    }

    private Map<String, String> namesOf(Collection<String> userIds) {
        List<String> valid = userIds == null ? List.of()
                : userIds.stream().filter(Objects::nonNull).distinct().toList();
        return orgService.namesOf(valid);
    }

    private Map<String, String> actionsOf(Collection<String> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        recordMapper.selectList(new LambdaQueryWrapper<BpmTaskRecord>()
                        .in(BpmTaskRecord::getTaskId, taskIds))
                .forEach(r -> result.putIfAbsent(r.getTaskId(), r.getAction()));
        return result;
    }

    private List<String> matchingInstanceIds(String keyword, String flowCode) {
        List<BpmInstance> matches = instanceMapper.selectList(new LambdaQueryWrapper<BpmInstance>()
                .like(BpmInstance::getTitle, keyword)
                .eq(flowCode != null && !flowCode.isBlank(), BpmInstance::getFlowCode, flowCode)
                .last("limit " + MAX_KEYWORD_HITS));
        // 待办/已办走 Flowable 引擎查询，需要引擎实例 ID；
        // 草稿或历史行可能没有 engineInstanceId，必须过滤掉空值，否则 Flowable 会抛
        // "None of the given process instance ids can be null" 导致整个查询失败。
        return matches.stream()
                .map(BpmInstance::getEngineInstanceId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
    }

    /**
     * 统计给定查询下「实例投影已不存在」的历史任务所涉实例数（去重）。
     * 仅在该页命中幽灵任务时调用，用于修正分页总数。
     */
    private long countOrphanInstances(HistoricTaskInstanceQuery query) {
        List<String> ids = query.list().stream()
                .map(HistoricTaskInstance::getProcessInstanceId)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return 0;
        }
        Map<String, BpmInstance> map = instanceMap(ids);
        return ids.stream().filter(id -> !map.containsKey(id)).count();
    }

    private int offset(int page, int size) {
        return Math.max(page - 1, 0) * size;
    }

    private String titleOf(BpmInstance instance) {
        return instance == null ? null : instance.getTitle();
    }

    private String flowCodeOf(BpmInstance instance) {
        return instance == null ? null : instance.getFlowCode();
    }

    private String flowNameOf(BpmInstance instance) {
        return instance == null ? null : instance.getFlowName();
    }

    private String initiatorOf(BpmInstance instance) {
        return instance == null ? null : instance.getInitiatorName();
    }

    private String sourceAppKeyOf(BpmInstance instance) {
        return instance == null ? null : instance.getSourceAppKey();
    }

    private String sourceAppNameOf(BpmInstance instance) {
        return instance == null ? null : instance.getSourceAppName();
    }

    private LocalDateTime toLocal(java.util.Date date) {
        return date == null ? null : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }
}
