package com.easybpm.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.easybpm.common.BizException;
import com.easybpm.common.ErrorCode;
import com.easybpm.common.Ids;
import com.easybpm.common.JsonCodec;
import com.easybpm.flow.ApprovalMode;
import com.easybpm.flow.BpmnCompiler;
import com.easybpm.flow.FlowDefinition;
import com.easybpm.flow.FlowDefinitionService;
import com.easybpm.flow.FlowNode;
import com.easybpm.flow.FlowSchema;
import com.easybpm.flow.EmptyStrategy;
import com.easybpm.flow.FormValidator;
import com.easybpm.flow.NodeType;
import com.easybpm.flow.RejectStrategy;
import com.easybpm.org.OrgService;
import com.easybpm.org.OrgUser;
import com.easybpm.tenant.TenantContext;
import com.easybpm.tenant.Tenant;
import com.easybpm.tenant.TenantMapper;
import com.easybpm.webhook.WebhookService;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 审批引擎门面。
 *
 * <p>所有 Flowable 调用都显式带 tenantId，业务代码无法跨租户操作流程实例；
 * 审批链的高级能力（或签/会签、条件节点、抄送）通过编译后的 BPMN 生效。
 *
 * <p>P2 新增：驳回策略 TO_START（退回发起人重提）、发起人撤回、任务转交。
 * TO_START 的实现采用「终止旧引擎实例 + 重提时重建新实例」的方式，
 * 对外 identity 恒定为投影 instanceId，接入方无感知。
 */
@Service
public class FlowEngineService {

    /** TO_START 驳回后实例停留在等待重提状态的展示名。 */
    public static final String PENDING_RESUBMIT_NODE = "等待发起人重新提交";

    private final FlowDefinitionService definitions;
    private final BpmnCompiler compiler;
    private final JsonCodec json;
    private final FormValidator formValidator;
    private final ApproverResolver approverResolver;
    private final ConditionEvaluator conditionEvaluator;
    private final OrgService orgService;
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;
    private final BpmInstanceMapper instanceMapper;
    private final BpmTaskRecordMapper recordMapper;
    private final TenantMapper tenantMapper;
    private final WebhookService webhookService;

    public FlowEngineService(FlowDefinitionService definitions, BpmnCompiler compiler, JsonCodec json,
                             FormValidator formValidator, ApproverResolver approverResolver,
                             ConditionEvaluator conditionEvaluator,
                             OrgService orgService, RuntimeService runtimeService, TaskService taskService,
                             HistoryService historyService, BpmInstanceMapper instanceMapper,
                             BpmTaskRecordMapper recordMapper, TenantMapper tenantMapper,
                             WebhookService webhookService) {
        this.definitions = definitions;
        this.compiler = compiler;
        this.json = json;
        this.formValidator = formValidator;
        this.approverResolver = approverResolver;
        this.conditionEvaluator = conditionEvaluator;
        this.orgService = orgService;
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.historyService = historyService;
        this.instanceMapper = instanceMapper;
        this.recordMapper = recordMapper;
        this.tenantMapper = tenantMapper;
        this.webhookService = webhookService;
    }

    @Transactional
    public StartResult start(StartCommand command) {
        String tenantId = TenantContext.require();

        // 幂等检查必须先于参数校验：重发请求即使缺字段也应返回原实例
        if (command.idempotencyKey() != null && !command.idempotencyKey().isBlank()) {
            BpmInstance existing = instanceMapper.selectOne(new LambdaQueryWrapper<BpmInstance>()
                    .eq(BpmInstance::getIdempotencyKey, command.idempotencyKey()));
            if (existing != null) {
                return new StartResult(existing.getInstanceId(), existing.getStatus(),
                        existing.getCurrentNodeName(), pendingTasks(existing.getEngineInstanceId()));
            }
        }

        if (command.flowCode() == null || command.flowCode().isBlank()) {
            throw BizException.badRequest("flowCode 不能为空");
        }
        if (command.initiatorId() == null || command.initiatorId().isBlank()) {
            throw BizException.badRequest("initiatorId 不能为空");
        }
        if (command.initiatorName() == null || command.initiatorName().isBlank()) {
            throw BizException.badRequest("initiatorName 不能为空");
        }

        FlowDefinition definition = definitions.requirePublished(command.flowCode());
        FlowSchema schema = json.read(definition.getFlowSchema(), FlowSchema.class);
        formValidator.validate(definition.getFormSchema(), command.formData());
        String title = command.title() == null || command.title().isBlank()
                ? definition.getName() + " - " + command.initiatorName()
                : command.title();

        ResolvedFlow resolved = resolveVariables(definition, schema, command, title);
        Map<String, Object> variables = resolved.variables();

        ProcessInstance processInstance;
        try {
            processInstance = runtimeService.startProcessInstanceByKeyAndTenantId(
                    compiler.processKeyOf(definition.getCode()), command.businessKey(), variables, tenantId);
        } catch (FlowableObjectNotFoundException e) {
            throw new BizException(ErrorCode.FLOW_NOT_FOUND, "流程未部署或已停用：" + command.flowCode());
        }

        LocalDateTime now = LocalDateTime.now();
        BpmInstance projection = new BpmInstance();
        projection.setId(Ids.id());
        // 首次发起：对外 ID 与引擎 ID 相同，此后对外 ID 恒定，重提只更换引擎 ID
        projection.setInstanceId(processInstance.getId());
        projection.setEngineInstanceId(processInstance.getId());
        projection.setFlowCode(definition.getCode());
        projection.setFlowName(definition.getName());
        projection.setFlowVersion(definition.getVersion());
        projection.setBusinessKey(command.businessKey());
        projection.setTitle(title);
        projection.setInitiatorId(command.initiatorId());
        projection.setInitiatorName(command.initiatorName());
        projection.setInitiatorDeptCode(resolveDeptCode(command));
        projection.setFormData(json.write(command.formData()));
        projection.setStatus(InstanceStatus.RUNNING.name());
        projection.setIdempotencyKey(blankToNull(command.idempotencyKey()));
        projection.setCallbackUrl(command.callbackUrl());
        projection.setStartedAt(now);
        projection.setUpdatedAt(now);
        instanceMapper.insert(projection);

        insertRecord(processInstance.getId(), null, null, "发起审批", command.initiatorId(),
                command.initiatorName(), TaskAction.SUBMIT, null);

        autoRun(processInstance.getId(), resolved);

        ActionResult current = syncProgress(processInstance.getId(), false);
        return new StartResult(processInstance.getId(), current.status(),
                current.currentNodeName(), pendingTasks(processInstance.getId()));
    }

    /**
     * 发起人重新提交（配合 TO_START 驳回）：以最新表单重新解析审批人/条件，重建引擎实例。
     */
    @Transactional
    public StartResult resubmit(String instanceId, String userId, Map<String, Object> formData, String comment) {
        BpmInstance projection = requireProjection(instanceId);
        if (!InstanceStatus.RUNNING.name().equals(projection.getStatus())) {
            throw new BizException(ErrorCode.INSTANCE_NOT_RUNNING);
        }
        if (!PENDING_RESUBMIT_NODE.equals(projection.getCurrentNodeName())) {
            throw BizException.badRequest("当前不在等待重新提交状态，无法重提");
        }
        if (!Objects.equals(projection.getInitiatorId(), userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "只有发起人可以重新提交");
        }

        FlowDefinition definition = definitions.requirePublished(projection.getFlowCode());
        FlowSchema schema = json.read(definition.getFlowSchema(), FlowSchema.class);
        formValidator.validate(definition.getFormSchema(), formData);

        Map<String, Object> merged = new LinkedHashMap<>(json.readMap(projection.getFormData()));
        if (formData != null) {
            merged.putAll(formData);
        }
        StartCommand command = new StartCommand(definition.getCode(), projection.getBusinessKey(),
                projection.getTitle(), projection.getInitiatorId(), projection.getInitiatorName(),
                projection.getInitiatorDeptCode(), merged, List.of(), projection.getCallbackUrl(), null);
        String title = projection.getTitle();
        ResolvedFlow resolved = resolveVariables(definition, schema, command, title);
        Map<String, Object> variables = resolved.variables();
        // 重提会更换引擎实例 ID，抄送节点需据此回写对外 ID
        variables.put(CcDelegate.INSTANCE_ID_VAR, projection.getInstanceId());

        ProcessInstance newProcess;
        try {
            newProcess = runtimeService.startProcessInstanceByKeyAndTenantId(
                    compiler.processKeyOf(definition.getCode()), projection.getBusinessKey(), variables,
                    TenantContext.require());
        } catch (FlowableObjectNotFoundException e) {
            throw new BizException(ErrorCode.FLOW_NOT_FOUND, "流程未部署或已停用：" + definition.getCode());
        }

        projection.setEngineInstanceId(newProcess.getId());
        projection.setFormData(json.write(merged));
        projection.setUpdatedAt(LocalDateTime.now());
        instanceMapper.updateById(projection);

        insertRecord(projection.getInstanceId(), null, null, "重新提交", userId,
                displayName(userId), TaskAction.RESUBMIT, comment);

        autoRun(newProcess.getId(), resolved);

        ActionResult current = syncProgress(newProcess.getId(), false);
        return new StartResult(projection.getInstanceId(), current.status(),
                current.currentNodeName(), pendingTasks(newProcess.getId()));
    }

    /**
     * 发起人撤回运行中的实例：终止引擎流程，投影标记 CANCELED。
     */
    @Transactional
    public ActionResult withdraw(String instanceId, String userId, String comment) {
        BpmInstance projection = requireProjection(instanceId);
        if (!InstanceStatus.RUNNING.name().equals(projection.getStatus())) {
            throw new BizException(ErrorCode.INSTANCE_NOT_RUNNING, "仅运行中的审批可以撤回");
        }
        if (!Objects.equals(projection.getInitiatorId(), userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "只有发起人可以撤回");
        }

        terminateQuietly(projection.getEngineInstanceId(), "WITHDRAW:" + userId);

        LocalDateTime now = LocalDateTime.now();
        projection.setStatus(InstanceStatus.CANCELED.name());
        projection.setCurrentNodeName("发起人撤回");
        projection.setEndedAt(now);
        projection.setUpdatedAt(now);
        instanceMapper.updateById(projection);

        enqueueWebhook(projection, "instance.canceled");

        insertRecord(projection.getInstanceId(), null, null, "撤回", userId,
                displayName(userId), TaskAction.REVOKE, comment);

        return new ActionResult(projection.getInstanceId(), projection.getStatus(),
                projection.getCurrentNodeName(), now);
    }

    @Transactional
    public ActionResult approve(String taskId, String userId, String comment, Map<String, Object> formData) {
        Task task = requireActiveTask(taskId, userId);
        String processInstanceId = task.getProcessInstanceId();
        String nodeId = task.getTaskDefinitionKey();
        String externalId = requireProjectionByEngine(processInstanceId).getInstanceId();

        runtimeService.setVariable(processInstanceId, "decision_" + nodeId, "APPROVE");
        if (formData != null && !formData.isEmpty()) {
            runtimeService.setVariables(processInstanceId, formData);
            mergeFormData(processInstanceId, formData);
        }
        addComment(taskId, processInstanceId, comment);
        taskService.complete(taskId);
        insertRecord(externalId, taskId, nodeId, task.getName(), userId,
                displayName(userId), TaskAction.APPROVE, comment);

        return syncProgress(processInstanceId, false);
    }

    @Transactional
    public ActionResult reject(String taskId, String userId, String comment) {
        Task task = requireActiveTask(taskId, userId);
        String processInstanceId = task.getProcessInstanceId();
        String nodeId = task.getTaskDefinitionKey();
        String externalId = requireProjectionByEngine(processInstanceId).getInstanceId();
        RejectStrategy strategy = rejectStrategyOf(processInstanceId, nodeId);

        if (strategy == RejectStrategy.TO_START) {
            return rejectToStart(task, userId, comment);
        }

        runtimeService.setVariable(processInstanceId, "decision_" + nodeId, "REJECT");
        addComment(taskId, processInstanceId, comment);
        taskService.complete(taskId);
        insertRecord(externalId, taskId, nodeId, task.getName(), userId,
                displayName(userId), TaskAction.REJECT, comment);

        return syncProgress(processInstanceId, true);
    }

    /**
     * 转交：将任务转给同租户其他在职人员，原处理人立即失去处理权。
     */
    @Transactional
    public ActionResult transfer(String taskId, String userId, String targetUserId, String comment) {
        Task task = requireActiveTask(taskId, userId);
        if (Objects.equals(task.getAssignee(), targetUserId)) {
            throw BizException.badRequest("不能转交给自己");
        }
        OrgUser target = orgService.findUser(targetUserId);
        if (target == null) {
            throw BizException.badRequest("目标处理人不存在：" + targetUserId);
        }
        if (!"ACTIVE".equals(target.getStatus())) {
            throw BizException.badRequest("目标处理人已停用：" + targetUserId);
        }

        String processInstanceId = task.getProcessInstanceId();
        taskService.setAssignee(taskId, targetUserId);
        String note = (comment == null || comment.isBlank() ? "" : comment + "；")
                + "转交给 " + target.getName();
        insertRecord(requireProjectionByEngine(processInstanceId).getInstanceId(), taskId,
                task.getTaskDefinitionKey(), task.getName(), userId,
                displayName(userId), TaskAction.TRANSFER, note);

        return syncProgress(processInstanceId, false);
    }

    /** TO_START：终止当前引擎实例，实例进入等待发起人重提状态。 */
    private ActionResult rejectToStart(Task task, String userId, String comment) {
        String processInstanceId = task.getProcessInstanceId();
        String externalId = requireProjectionByEngine(processInstanceId).getInstanceId();
        runtimeService.setVariable(processInstanceId, "decision_" + task.getTaskDefinitionKey(), "REJECT");
        addComment(task.getId(), processInstanceId, comment);
        taskService.complete(task.getId());
        insertRecord(externalId, task.getId(), task.getTaskDefinitionKey(), task.getName(), userId,
                displayName(userId), TaskAction.REJECT, comment);

        terminateQuietly(processInstanceId, "REJECT_TO_START");

        BpmInstance projection = requireProjectionByEngine(processInstanceId);
        projection.setStatus(InstanceStatus.RUNNING.name());
        projection.setCurrentNodeName(PENDING_RESUBMIT_NODE);
        projection.setEndedAt(null);
        projection.setUpdatedAt(LocalDateTime.now());
        instanceMapper.updateById(projection);

        return new ActionResult(externalId, projection.getStatus(),
                PENDING_RESUBMIT_NODE, null);
    }

    private RejectStrategy rejectStrategyOf(String processInstanceId, String nodeId) {
        BpmInstance projection = requireProjectionByEngine(processInstanceId);
        FlowDefinition definition = definitions.find(projection.getFlowCode());
        if (definition == null || definition.getFlowSchema() == null) {
            return RejectStrategy.TO_END;
        }
        FlowSchema schema = json.read(definition.getFlowSchema(), FlowSchema.class);
        return schema.nodes().stream()
                .filter(n -> nodeId.equals(n.id()))
                .findFirst()
                .map(n -> n.props() == null ? RejectStrategy.TO_END : n.props().rejectOrDefault())
                .orElse(RejectStrategy.TO_END);
    }

    private void terminateQuietly(String processInstanceId, String reason) {
        try {
            runtimeService.deleteProcessInstance(processInstanceId, reason);
        } catch (FlowableObjectNotFoundException ignored) {
            // 引擎实例已不存在（如已被其他操作终止），投影状态以数据库为准
        }
    }

    /** 按对外实例 ID 查投影（resubmit / withdraw 等入口）。 */
    private BpmInstance requireProjection(String instanceId) {
        BpmInstance projection = instanceMapper.selectOne(new LambdaQueryWrapper<BpmInstance>()
                .eq(BpmInstance::getInstanceId, instanceId));
        if (projection == null) {
            throw new BizException(ErrorCode.INSTANCE_NOT_FOUND, "审批实例不存在：" + instanceId);
        }
        return projection;
    }

    /** 按引擎实例 ID 查投影（Flowable 回调路径）。 */
    private BpmInstance requireProjectionByEngine(String engineInstanceId) {
        BpmInstance projection = instanceMapper.selectOne(new LambdaQueryWrapper<BpmInstance>()
                .eq(BpmInstance::getEngineInstanceId, engineInstanceId));
        if (projection == null) {
            throw new BizException(ErrorCode.INSTANCE_NOT_FOUND, "审批实例不存在：" + engineInstanceId);
        }
        return projection;
    }

    /** 发起变量解析：条件求值、审批人解析、抄送人合并。发起与重提共用。 */
    private record ResolvedFlow(Map<String, Object> variables, Set<String> autoPassNodes, Set<String> autoRejectNodes) {
    }

    /** 审批人为空兜底时占位的任务处理人，任务创建后随即由系统自动处理。 */
    private static final String AUTO_ASSIGNEE = "__system_auto__";
    private static final String AUTO_OPERATOR = "__system__";
    private static final String AUTO_PASS_COMMENT = "审批人为空，按节点兜底策略自动通过";
    private static final String AUTO_REJECT_COMMENT = "审批人为空，按节点兜底策略自动驳回";

    private ResolvedFlow resolveVariables(FlowDefinition definition, FlowSchema schema,
                                          StartCommand command, String title) {
        Map<String, Object> variables = new LinkedHashMap<>(command.formData());
        variables.put("initiatorId", command.initiatorId());
        variables.put("initiatorName", command.initiatorName());
        if (command.initiatorDeptCode() != null) {
            variables.put("initiatorDeptCode", command.initiatorDeptCode());
        }
        variables.put("instanceTitle", title);

        Set<String> autoPassNodes = new LinkedHashSet<>();
        Set<String> autoRejectNodes = new LinkedHashSet<>();
        List<FlowNode> nodes = schema.nodes().subList(1, schema.nodes().size() - 1);
        for (FlowNode node : nodes) {
            if (node.condition() != null && !node.condition().isBlank()) {
                variables.put("cond_" + node.id(), conditionEvaluator.evaluate(node.condition(), variables, node.name()));
            }
            if (node.type() == NodeType.APPROVAL) {
                List<String> approvers = approverResolver.resolve(node, command, variables);
                EmptyStrategy emptyStrategy = node.props().emptyOrDefault();
                if (approvers.isEmpty() && emptyStrategy != EmptyStrategy.FAIL) {
                    // 兜底：占位处理人让引擎创建任务，随后由 autoRun 按策略系统处理
                    (emptyStrategy == EmptyStrategy.AUTO_PASS ? autoPassNodes : autoRejectNodes).add(node.id());
                    variables.put("ap_" + node.id(), List.of(AUTO_ASSIGNEE));
                } else {
                    approverResolver.requireNotEmpty(approvers, node);
                    variables.put("ap_" + node.id(), approvers);
                }
                variables.put("decision_" + node.id(), "PENDING");
            } else if (node.type() == NodeType.CC) {
                List<String> ccUsers = new ArrayList<>(node.props().ccUsers());
                // 发起请求可能不带 ccUsers（仅可选补充），null 时只看节点自身配置
                if (command.ccUsers() != null) {
                    ccUsers.addAll(command.ccUsers());
                }
                variables.put("cc_" + node.id(), new ArrayList<>(new LinkedHashSet<>(ccUsers)));
            }
        }
        return new ResolvedFlow(new HashMap<>(variables), autoPassNodes, autoRejectNodes);
    }

    /**
     * 发起后自动执行兜底节点：审批人解析为空的 AUTO_PASS / AUTO_REJECT 节点，
     * 以系统身份完成对应任务，写入时间线。循环处理以覆盖连续多个兜底节点的场景。
     */
    private void autoRun(String processInstanceId, ResolvedFlow resolved) {
        if (resolved.autoPassNodes().isEmpty() && resolved.autoRejectNodes().isEmpty()) {
            return;
        }
        String externalId = requireProjectionByEngine(processInstanceId).getInstanceId();
        boolean rejected = false;
        boolean progressed = true;
        while (progressed && !rejected) {
            progressed = false;
            for (Task task : taskService.createTaskQuery().processInstanceId(processInstanceId).list()) {
                String nodeId = task.getTaskDefinitionKey();
                if (resolved.autoPassNodes().contains(nodeId)) {
                    runtimeService.setVariable(processInstanceId, "decision_" + nodeId, "APPROVE");
                    insertRecord(externalId, task.getId(), nodeId, task.getName(), AUTO_OPERATOR,
                            "系统", TaskAction.APPROVE, AUTO_PASS_COMMENT);
                    taskService.complete(task.getId());
                    progressed = true;
                } else if (resolved.autoRejectNodes().contains(nodeId)) {
                    runtimeService.setVariable(processInstanceId, "decision_" + nodeId, "REJECT");
                    insertRecord(externalId, task.getId(), nodeId, task.getName(), AUTO_OPERATOR,
                            "系统", TaskAction.REJECT, AUTO_REJECT_COMMENT);
                    taskService.complete(task.getId());
                    rejected = true;
                }
            }
        }
    }

    private Task requireActiveTask(String taskId, String userId) {
        String tenantId = TenantContext.require();
        Task task = taskService.createTaskQuery().taskId(taskId).taskTenantId(tenantId).singleResult();
        if (task == null) {
            HistoricTaskInstance historic = historyService.createHistoricTaskInstanceQuery()
                    .taskId(taskId).taskTenantId(tenantId).singleResult();
            if (historic != null) {
                throw new BizException(ErrorCode.TASK_ALREADY_HANDLED);
            }
            throw new BizException(ErrorCode.TASK_NOT_FOUND);
        }
        if (!Objects.equals(task.getAssignee(), userId)) {
            throw new BizException(ErrorCode.TASK_NOT_ASSIGNEE);
        }
        return task;
    }

    private ActionResult syncProgress(String processInstanceId, boolean rejected) {
        BpmInstance projection = requireProjectionByEngine(processInstanceId);
        ProcessInstance running = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId).singleResult();
        LocalDateTime now = LocalDateTime.now();
        if (running == null) {
            String status = rejected ? InstanceStatus.REJECTED.name() : InstanceStatus.APPROVED.name();
            projection.setStatus(status);
            projection.setEndedAt(now);
            projection.setCurrentNodeName(rejected ? projection.getCurrentNodeName() : "审批通过");
            enqueueWebhook(projection, "instance.completed");
        } else {
            projection.setStatus(InstanceStatus.RUNNING.name());
            projection.setCurrentNodeName(activeNodeNames(processInstanceId));
        }
        projection.setUpdatedAt(now);
        instanceMapper.updateById(projection);
        return new ActionResult(processInstanceId, projection.getStatus(),
                projection.getCurrentNodeName(), projection.getEndedAt());
    }

    /**
     * 实例结束时进入 outbox：事务内写 PENDING 行，由 WebhookService 轮询投递与重试。
     * 回调地址优先取发起时指定的 callback_url，否则回退租户 webhook_url。
     */
    private void enqueueWebhook(BpmInstance projection, String eventType) {
        try {
            String url = projection.getCallbackUrl();
            if (url == null || url.isBlank()) {
                Tenant tenant = tenantMapper.selectById(TenantContext.require());
                url = tenant == null ? null : tenant.getWebhookUrl();
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("event", eventType);
            payload.put("instanceId", projection.getInstanceId());
            payload.put("flowCode", projection.getFlowCode());
            payload.put("flowName", projection.getFlowName());
            payload.put("title", projection.getTitle());
            payload.put("status", projection.getStatus());
            payload.put("businessKey", projection.getBusinessKey());
            payload.put("initiatorId", projection.getInitiatorId());
            payload.put("initiatorName", projection.getInitiatorName());
            payload.put("endedAt", projection.getEndedAt() == null ? null : projection.getEndedAt().toString());
            payload.put("formData", json.readMap(projection.getFormData()));
            webhookService.enqueue(TenantContext.require(), projection.getInstanceId(), eventType, url, payload);
        } catch (Exception e) {
            // 回调入队失败不影响审批主流程，PENDING 行缺失可通过审计与日志排查
            org.slf4j.LoggerFactory.getLogger(FlowEngineService.class)
                    .warn("webhook enqueue 失败 instance={} event={}", projection.getInstanceId(), eventType, e);
        }
    }

    private String activeNodeNames(String processInstanceId) {
        List<Task> tasks = taskService.createTaskQuery().processInstanceId(processInstanceId).list();
        if (tasks.isEmpty()) {
            return null;
        }
        return new LinkedHashSet<>(tasks.stream().map(Task::getName).toList()).stream()
                .reduce((a, b) -> a + " / " + b).orElse(null);
    }

    private List<TaskView> pendingTasks(String processInstanceId) {
        BpmInstance projection = requireProjectionByEngine(processInstanceId);
        Map<String, String> names = orgService.namesOf(taskService.createTaskQuery()
                .processInstanceId(processInstanceId).list().stream()
                .map(Task::getAssignee).filter(Objects::nonNull).toList());
        return taskService.createTaskQuery().processInstanceId(processInstanceId)
                .orderByTaskCreateTime().asc().list().stream()
                .map(task -> toView(task, projection, names))
                .toList();
    }

    private TaskView toView(Task task, BpmInstance projection, Map<String, String> names) {
        return new TaskView(task.getId(), task.getProcessInstanceId(), task.getTaskDefinitionKey(),
                task.getName(), task.getAssignee(),
                names.getOrDefault(task.getAssignee(), task.getAssignee()),
                projection == null ? null : projection.getTitle(),
                projection == null ? null : projection.getFlowCode(),
                projection == null ? null : projection.getFlowName(),
                projection == null ? null : projection.getInitiatorName(),
                "PENDING", null, task.getCreateTime() == null ? null
                : LocalDateTime.ofInstant(task.getCreateTime().toInstant(), java.time.ZoneId.systemDefault()),
                null);
    }

    private void mergeFormData(String processInstanceId, Map<String, Object> formData) {
        BpmInstance projection = requireProjectionByEngine(processInstanceId);
        Map<String, Object> merged = new LinkedHashMap<>(json.readMap(projection.getFormData()));
        merged.putAll(formData);
        projection.setFormData(json.write(merged));
        projection.setUpdatedAt(LocalDateTime.now());
        instanceMapper.updateById(projection);
    }

    private void insertRecord(String processInstanceId, String taskId, String nodeId, String nodeName,
                              String operatorId, String operatorName, TaskAction action, String comment) {
        BpmTaskRecord record = new BpmTaskRecord();
        record.setId(Ids.id());
        record.setInstanceId(processInstanceId);
        record.setTaskId(taskId);
        record.setNodeId(nodeId);
        record.setNodeName(nodeName);
        record.setOperatorId(operatorId);
        record.setOperatorName(operatorName);
        record.setAction(action.name());
        record.setComment(comment);
        record.setCreatedAt(LocalDateTime.now());
        recordMapper.insert(record);
    }

    private void addComment(String taskId, String processInstanceId, String comment) {
        if (comment != null && !comment.isBlank()) {
            taskService.addComment(taskId, processInstanceId, comment);
        }
    }

    private String displayName(String userId) {
        OrgUser user = orgService.findUser(userId);
        return user == null ? userId : user.getName();
    }

    private String resolveDeptCode(StartCommand command) {
        if (command.initiatorDeptCode() != null && !command.initiatorDeptCode().isBlank()) {
            return command.initiatorDeptCode();
        }
        OrgUser user = orgService.findUser(command.initiatorId());
        return user == null ? null : user.getDeptCode();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * 校验审批链是否可以发起（不启动实例），供定义页预检。
     */
    public void validateForStart(String flowCode, StartCommand command) {
        FlowDefinition definition = definitions.requirePublished(flowCode);
        FlowSchema schema = json.read(definition.getFlowSchema(), FlowSchema.class);
        Map<String, Object> variables = new LinkedHashMap<>(command.formData());
        variables.put("initiatorId", command.initiatorId());
        variables.put("initiatorDeptCode", command.initiatorDeptCode());
        for (FlowNode node : schema.nodes().subList(1, schema.nodes().size() - 1)) {
            if (node.type() == NodeType.APPROVAL
                    && (node.condition() == null || conditionEvaluator.evaluate(node.condition(), variables, node.name()))) {
                List<String> approvers = approverResolver.resolve(node, command, variables);
                // 配置了自动通过/自动驳回兜底的节点，审批人为空不算发起阻塞
                if (!approvers.isEmpty() || node.props().emptyOrDefault() != EmptyStrategy.FAIL) {
                    approverResolver.requireNotEmpty(approvers, node);
                }
            }
        }
    }
}
