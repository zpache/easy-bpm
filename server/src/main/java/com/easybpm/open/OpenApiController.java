package com.easybpm.open;

import com.easybpm.common.ApiResponse;
import com.easybpm.common.JsonCodec;
import com.easybpm.common.PageResult;
import com.easybpm.engine.ActionResult;
import com.easybpm.engine.CcView;
import com.easybpm.engine.FlowEngineService;
import com.easybpm.engine.InstanceDetail;
import com.easybpm.engine.InstanceView;
import com.easybpm.engine.StartCommand;
import com.easybpm.engine.StartResult;
import com.easybpm.engine.TaskView;
import com.easybpm.engine.WorkbenchCounts;
import com.easybpm.engine.WorkbenchQueryService;
import com.easybpm.flow.FlowDefinition;
import com.easybpm.flow.FlowDefinitionService;
import com.easybpm.org.OrgDept;
import com.easybpm.org.OrgService;
import com.easybpm.org.OrgUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 对外开放接口。ERP / SaaS 只需要这一组接口即可完成审批闭环。
 */
@RestController
@RequestMapping("/open/v1")
@Tag(name = "OpenAPI", description = "第三方系统接入的审批接口")
public class OpenApiController {

    private final FlowEngineService engine;
    private final WorkbenchQueryService query;
    private final FlowDefinitionService definitions;
    private final OrgService orgService;
    private final JsonCodec json;

    public OpenApiController(FlowEngineService engine, WorkbenchQueryService query,
                             FlowDefinitionService definitions, OrgService orgService, JsonCodec json) {
        this.engine = engine;
        this.query = query;
        this.definitions = definitions;
        this.orgService = orgService;
        this.json = json;
    }

    @GetMapping("/flows")
    @Operation(summary = "查询当前租户已发布的审批流程")
    public ApiResponse<List<FlowSummary>> flows() {
        return ApiResponse.ok(definitions.listPublished().stream().map(this::toSummary).toList());
    }

    @PostMapping("/instances")
    @Operation(summary = "创建并提交审批", description = "一次调用完成发起；返回实例ID与当前待办任务。幂等键优先于参数校验")
    public ApiResponse<StartResult> start(@RequestBody OpenDtos.StartRequest request) {
        StartCommand command = new StartCommand(request.flowCode(), request.businessKey(), request.title(),
                request.initiatorId(), request.initiatorName(), request.initiatorDeptCode(),
                request.formData(), request.ccUsers(), request.callbackUrl(), request.idempotencyKey());
        return ApiResponse.ok(engine.start(command));
    }

    @PostMapping("/instances/{instanceId}/resubmit")
    @Operation(summary = "重新提交", description = "TO_START 驳回后，发起人修改表单重新提交，流程从头流转")
    public ApiResponse<StartResult> resubmit(@PathVariable String instanceId,
                                             @Valid @RequestBody OpenDtos.ResubmitRequest request) {
        return ApiResponse.ok(engine.resubmit(instanceId, request.userId(), request.formData(), request.comment()));
    }

    @PostMapping("/instances/{instanceId}/withdraw")
    @Operation(summary = "撤回", description = "发起人撤回运行中的审批，实例状态变为 CANCELED")
    public ApiResponse<ActionResult> withdraw(@PathVariable String instanceId,
                                              @Valid @RequestBody OpenDtos.WithdrawRequest request) {
        return ApiResponse.ok(engine.withdraw(instanceId, request.userId(), request.comment()));
    }

    @GetMapping("/instances/{instanceId}")
    @Operation(summary = "查询审批详情与时间线")
    public ApiResponse<InstanceDetail> detail(@PathVariable String instanceId) {
        return ApiResponse.ok(query.detail(instanceId));
    }

    @GetMapping("/instances/submitted")
    @Operation(summary = "查询我提交的审批")
    public ApiResponse<PageResult<InstanceView>> submitted(
            @RequestParam String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String flowCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(query.submitted(userId, page(page), size(size), flowCode, status, keyword));
    }

    @GetMapping("/tasks/todo")
    @Operation(summary = "查询待办任务")
    public ApiResponse<PageResult<TaskView>> todo(
            @RequestParam String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String flowCode,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(query.todo(userId, page(page), size(size), flowCode, keyword));
    }

    @GetMapping("/tasks/done")
    @Operation(summary = "查询已办任务")
    public ApiResponse<PageResult<TaskView>> done(
            @RequestParam String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String flowCode,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(query.done(userId, page(page), size(size), flowCode, keyword));
    }

    @GetMapping("/tasks/count")
    @Operation(summary = "查询待办/已办/已提交数量")
    public ApiResponse<WorkbenchCounts> counts(@RequestParam String userId) {
        return ApiResponse.ok(query.counts(userId));
    }

    @GetMapping("/cc")
    @Operation(summary = "查询抄送我的", description = "按接收人分页查询抄送记录，unreadOnly=true 只看未读")
    public ApiResponse<PageResult<CcView>> cc(
            @RequestParam String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(query.cc(userId, page(page), size(size), unreadOnly, keyword));
    }

    @PostMapping("/cc/{id}/read")
    @Operation(summary = "标记抄送已读", description = "只能标记自己的抄送记录，重复调用幂等")
    public ApiResponse<Void> readCc(@PathVariable String id, @RequestParam String userId) {
        query.markCcRead(id, userId);
        return ApiResponse.ok();
    }

    @PostMapping("/tasks/{taskId}/approve")
    @Operation(summary = "同意", description = "会签节点需全部同意；或签节点一人同意即流转")
    public ApiResponse<ActionResult> approve(@PathVariable String taskId,
                                             @Valid @RequestBody OpenDtos.HandleRequest request) {
        return ApiResponse.ok(engine.approve(taskId, request.userId(), request.comment(), request.formData()));
    }

    @PostMapping("/tasks/{taskId}/reject")
    @Operation(summary = "驳回", description = "按节点驳回策略：TO_END 实例结束（REJECTED），TO_START 退回发起人重新提交")
    public ApiResponse<ActionResult> reject(@PathVariable String taskId,
                                            @Valid @RequestBody OpenDtos.HandleRequest request) {
        return ApiResponse.ok(engine.reject(taskId, request.userId(), request.comment()));
    }

    @PostMapping("/tasks/{taskId}/transfer")
    @Operation(summary = "转交", description = "将待办转交给同租户其他在职人员")
    public ApiResponse<ActionResult> transfer(@PathVariable String taskId,
                                              @Valid @RequestBody OpenDtos.TransferRequest request) {
        return ApiResponse.ok(engine.transfer(taskId, request.userId(), request.targetUserId(), request.comment()));
    }

    @PostMapping("/org/sync")
    @Operation(summary = "组织同步", description = "批量 upsert 人员 / 部门 / 角色，按编码幂等")
    public ApiResponse<OrgSyncResult> orgSync(@Valid @RequestBody OpenDtos.OrgSyncRequest request) {
        int users = 0;
        if (request.users() != null) {
            for (OpenDtos.OrgSyncRequest.OrgUserSync u : request.users()) {
                OrgUser user = new OrgUser();
                user.setUserCode(u.userCode());
                user.setName(u.name());
                user.setDeptCode(u.deptCode());
                user.setDeptName(u.deptName());
                user.setLeaderCode(u.leaderCode());
                user.setTitle(u.title());
                user.setMobile(u.mobile());
                user.setEmail(u.email());
                user.setStatus(u.status());
                users += orgService.upsertUser(user);
            }
        }
        int depts = 0;
        if (request.depts() != null) {
            for (OpenDtos.OrgSyncRequest.OrgDeptSync d : request.depts()) {
                OrgDept dept = new OrgDept();
                dept.setDeptCode(d.deptCode());
                dept.setName(d.name());
                dept.setParentCode(d.parentCode());
                dept.setLeaderId(d.leaderId());
                dept.setSortNo(d.sortNo());
                depts += orgService.upsertDept(dept);
            }
        }
        int roles = 0;
        if (request.roles() != null) {
            for (OpenDtos.OrgSyncRequest.OrgRoleSync r : request.roles()) {
                roles += orgService.replaceRoles(r.userCode(), r.roleCodes());
            }
        }
        return ApiResponse.ok(new OrgSyncResult(users, depts, roles));
    }

    private FlowSummary toSummary(FlowDefinition definition) {
        Object formSchema = definition.getFormSchema() == null ? null : json.readMap(definition.getFormSchema());
        return new FlowSummary(definition.getCode(), definition.getName(), definition.getCategory(),
                definition.getDescription(), definition.getVersion(), formSchema);
    }

    private int page(int page) {
        return Math.max(page, 1);
    }

    private int size(int size) {
        return Math.min(Math.max(size, 1), 100);
    }

    public record FlowSummary(String code, String name, String category, String description,
                              Integer version, Object formSchema) {
    }

    public record OrgSyncResult(int users, int depts, int roles) {
    }
}
