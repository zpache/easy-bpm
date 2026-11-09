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
import com.easybpm.flow.NodeType;
import com.easybpm.org.OrgService;
import com.easybpm.org.OrgUser;
import com.easybpm.tenant.TenantContext;
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

/**
 * 审批引擎门面。
 *
 * <p>所有 Flowable 调用都显式带 tenantId，业务代码无法跨租户操作流程实例；
 * 审批链的高级能力（或签/会签、条件节点、抄送）通过编译后的 BPMN 生效。
 */
@Service
public class FlowEngineService {

    private final FlowDefinitionService definitions;
    private final BpmnCompiler compiler;
    private final JsonCodec json;
    private final ApproverResolver approverResolver;
    private final ConditionEvaluator conditionEvaluator;
    private final OrgService orgService;
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;
    private final BpmInstanceMapper instanceMapper;
    private final BpmTaskRecordMapper recordMapper;

    public FlowEngineService(FlowDefinitionService definitions, BpmnCompiler compiler, JsonCodec json,
                             ApproverResolver approverResolver, ConditionEvaluator conditionEvaluator,
                             OrgService orgService, RuntimeService runtimeService, TaskService taskService,
                             HistoryService historyService, BpmInstanceMapper instanceMapper,
                             BpmTaskRecordMapper recordMapper) {
        this.definitions = definitions;
        this.compiler = compiler;
        this.json = json;
        this.approverResolver = approverResolver;
        this.conditionEvaluator = conditionEvaluator;
        this.orgService = orgService;
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.historyService = historyService;
        this.instanceMapper = instanceMapper;
        this.recordMapper = recordMapper;
    }

    @Transactional
    public StartResult start(StartCommand command) {
        String tenantId = TenantContext.require();
        if (command.initiatorId() == null || command.initiatorId().isBlank()) {
            throw BizException.badRequest("initiatorId 不能为空");
        }

        FlowDefinition definition = definitions.requirePublished(command.flowCode());
        if (command.idempotencyKey() != null && !command.idempotencyKey().isBlank()) {
            BpmInstance existing = instanceMapper.selectOne(new LambdaQueryWrapper<BpmInstance>()
                    .eq(BpmInstance::getIdempotencyKey, command.idempotencyKey()));
            if (existing != null) {
                return new StartResult(existing.getInstanceId(), existing.getStatus(),
                        existing.getCurrentNodeName(), pendingTasks(existing.getInstanceId()));
            }
        }

        FlowSchema schema = json.read(definition.getFlowSchema(), FlowSchema.class);
        String title = command.title() == null || command.title().isBlank()
                ? definition.getName() + " - " + command.initiatorName()
                : command.title();

        Map<String, Object> variables = new LinkedHashMap<>(command.formData());
        variables.put("initiatorId", command.initiatorId());
        variables.put("initiatorName", command.initiatorName());
        if (command.initiatorDeptCode() != null) {
            variables.put("initiatorDeptCode", command.initiatorDeptCode());
        }
        variables.put("instanceTitle", title);

        List<FlowNode> nodes = schema.nodes().subList(1, schema.nodes().size() - 1);
        for (FlowNode node : nodes) {
            if (node.condition() != null && !node.condition().isBlank()) {
                variables.put("cond_" + node.id(), conditionEvaluator.evaluate(node.condition(), variables, node.name()));
            }
            if (node.type() == NodeType.APPROVAL) {
                List<String> approvers = approverResolver.resolve(node, command, variables);
                approverResolver.requireNotEmpty(approvers, node);
                variables.put("ap_" + node.id(), approvers);
                variables.put("decision_" + node.id(), "PENDING");
            } else if (node.type() == NodeType.CC) {
                List<String> ccUsers = new ArrayList<>(node.props().ccUsers());
                ccUsers.addAll(command.ccUsers());
                variables.put("cc_" + node.id(), new ArrayList<>(new LinkedHashSet<>(ccUsers)));
            }
        }

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
        projection.setInstanceId(processInstance.getId());
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

        insertRecord(processInstance.getId(), null, "start", "发起审批", command.initiatorId(),
                command.initiatorName(), TaskAction.SUBMIT, null);

        ActionResult current = syncProgress(processInstance.getId(), false);
        projection.setStatus(current.status());
        projection.setCurrentNodeName(current.currentNodeName());
        if (!InstanceStatus.RUNNING.name().equals(current.status())) {
            projection.setEndedAt(LocalDateTime.now());
        }
        instanceMapper.updateById(projection);

        return new StartResult(processInstance.getId(), projection.getStatus(),
                projection.getCurrentNodeName(), pendingTasks(processInstance.getId()));
    }

    @Transactional
    public ActionResult approve(String taskId, String userId, String comment, Map<String, Object> formData) {
        Task task = requireActiveTask(taskId, userId);
        String processInstanceId = task.getProcessInstanceId();
        String nodeId = task.getTaskDefinitionKey();

        runtimeService.setVariable(processInstanceId, "decision_" + nodeId, "APPROVE");
        if (formData != null && !formData.isEmpty()) {
            runtimeService.setVariables(processInstanceId, formData);
            mergeFormData(processInstanceId, formData);
        }
        addComment(taskId, processInstanceId, comment);
        taskService.complete(taskId);
        insertRecord(processInstanceId, taskId, nodeId, task.getName(), userId,
                displayName(userId), TaskAction.APPROVE, comment);

        return syncProgress(processInstanceId, false);
    }

    @Transactional
    public ActionResult reject(String taskId, String userId, String comment) {
        Task task = requireActiveTask(taskId, userId);
        String processInstanceId = task.getProcessInstanceId();
        String nodeId = task.getTaskDefinitionKey();

        runtimeService.setVariable(processInstanceId, "decision_" + nodeId, "REJECT");
        addComment(taskId, processInstanceId, comment);
        taskService.complete(taskId);
        insertRecord(processInstanceId, taskId, nodeId, task.getName(), userId,
                displayName(userId), TaskAction.REJECT, comment);

        return syncProgress(processInstanceId, true);
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
        BpmInstance projection = instanceMapper.selectOne(new LambdaQueryWrapper<BpmInstance>()
                .eq(BpmInstance::getInstanceId, processInstanceId));
        if (projection == null) {
            throw new BizException(ErrorCode.INSTANCE_NOT_FOUND);
        }
        ProcessInstance running = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId).singleResult();
        LocalDateTime now = LocalDateTime.now();
        if (running == null) {
            String status = rejected ? InstanceStatus.REJECTED.name() : InstanceStatus.APPROVED.name();
            projection.setStatus(status);
            projection.setEndedAt(now);
            projection.setCurrentNodeName(rejected ? projection.getCurrentNodeName() : "审批通过");
        } else {
            projection.setStatus(InstanceStatus.RUNNING.name());
            projection.setCurrentNodeName(activeNodeNames(processInstanceId));
        }
        projection.setUpdatedAt(now);
        instanceMapper.updateById(projection);
        return new ActionResult(processInstanceId, projection.getStatus(), projection.getCurrentNodeName());
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
        BpmInstance projection = instanceMapper.selectOne(new LambdaQueryWrapper<BpmInstance>()
                .eq(BpmInstance::getInstanceId, processInstanceId));
        Map<String, String> names = projection == null ? Map.of()
                : orgService.namesOf(taskService.createTaskQuery().processInstanceId(processInstanceId)
                .list().stream().map(Task::getAssignee).filter(Objects::nonNull).toList());
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
        BpmInstance projection = instanceMapper.selectOne(new LambdaQueryWrapper<BpmInstance>()
                .eq(BpmInstance::getInstanceId, processInstanceId));
        if (projection == null) {
            return;
        }
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
                approverResolver.requireNotEmpty(approverResolver.resolve(node, command, variables), node);
            }
        }
    }
}
