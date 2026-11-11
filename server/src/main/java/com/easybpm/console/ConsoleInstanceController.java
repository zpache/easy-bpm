package com.easybpm.console;

import com.easybpm.audit.AuditService;
import com.easybpm.auth.AuthPrincipal;
import com.easybpm.auth.CurrentUser;
import com.easybpm.common.ApiResponse;
import com.easybpm.common.BizException;
import com.easybpm.common.ErrorCode;
import com.easybpm.common.PageResult;
import com.easybpm.engine.ActionResult;
import com.easybpm.engine.FlowEngineService;
import com.easybpm.engine.InstanceView;
import com.easybpm.engine.WorkbenchQueryService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员视角的实例查询：本租户全部审批实例（区别于工作台「我发起的」），
 * 以及运行中实例的救援操作（强制转办）。
 */
@RestController
@RequestMapping("/api/instances")
public class ConsoleInstanceController {

    private final WorkbenchQueryService query;
    private final FlowEngineService engine;
    private final AuditService audit;

    public ConsoleInstanceController(WorkbenchQueryService query, FlowEngineService engine, AuditService audit) {
        this.query = query;
        this.engine = engine;
        this.audit = audit;
    }

    @GetMapping
    public ApiResponse<PageResult<InstanceView>> list(@RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size,
                                                      @RequestParam(required = false) String flowCode,
                                                      @RequestParam(required = false) String status,
                                                      @RequestParam(required = false) String keyword) {
        requireAdmin();
        return ApiResponse.ok(query.allInstances(Math.max(page, 1), Math.min(Math.max(size, 1), 100),
                flowCode, status, keyword));
    }

    @PostMapping("/{instanceId}/force-transfer")
    @Operation(summary = "管理员强制转办：将运行中实例的待办改派给其他在职人员（卡单救援）")
    public ApiResponse<ActionResult> forceTransfer(@PathVariable String instanceId,
                                                   @Valid @RequestBody ForceTransferRequest request) {
        AuthPrincipal operator = requireAdmin();
        ActionResult result = engine.forceTransfer(instanceId, request.taskId(), operator.approverId(),
                request.targetUserCode(), request.comment());
        audit.record("ADMIN_TRANSFER", "INSTANCE", instanceId,
                "任务 " + request.taskId() + " 强制转办给 " + request.targetUserCode()
                        + (request.comment() == null || request.comment().isBlank() ? "" : "；" + request.comment()));
        return ApiResponse.ok(result);
    }

    private AuthPrincipal requireAdmin() {
        AuthPrincipal operator = CurrentUser.require();
        if (!operator.isTenantAdmin()) {
            throw new BizException(ErrorCode.FORBIDDEN, "需要管理员权限");
        }
        return operator;
    }

    public record ForceTransferRequest(@NotBlank String taskId,
                                       @NotBlank String targetUserCode,
                                       String comment) {
    }
}
