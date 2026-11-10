package com.easybpm.console;

import com.easybpm.auth.CurrentUser;
import com.easybpm.common.ApiResponse;
import com.easybpm.common.PageResult;
import com.easybpm.engine.ActionResult;
import com.easybpm.engine.CcView;
import com.easybpm.engine.FlowEngineService;
import com.easybpm.engine.InstanceDetail;
import com.easybpm.engine.InstanceView;
import com.easybpm.engine.TaskView;
import com.easybpm.engine.WorkbenchCounts;
import com.easybpm.engine.WorkbenchQueryService;
import com.easybpm.open.OpenDtos;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 控制台工作台。审批人身份取登录账号绑定的 externalUserId。
 */
@RestController
@RequestMapping("/api/workbench")
public class ConsoleWorkbenchController {

    private final WorkbenchQueryService query;
    private final FlowEngineService engine;

    public ConsoleWorkbenchController(WorkbenchQueryService query, FlowEngineService engine) {
        this.query = query;
        this.engine = engine;
    }

    @GetMapping("/todo")
    public ApiResponse<PageResult<TaskView>> todo(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size,
                                                  @RequestParam(required = false) String flowCode,
                                                  @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(query.todo(CurrentUser.requireApproverId(), page(page), size(size), flowCode, keyword));
    }

    @GetMapping("/done")
    public ApiResponse<PageResult<TaskView>> done(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size,
                                                  @RequestParam(required = false) String flowCode,
                                                  @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(query.done(CurrentUser.requireApproverId(), page(page), size(size), flowCode, keyword));
    }

    @GetMapping("/submitted")
    public ApiResponse<PageResult<InstanceView>> submitted(@RequestParam(defaultValue = "1") int page,
                                                           @RequestParam(defaultValue = "20") int size,
                                                           @RequestParam(required = false) String flowCode,
                                                           @RequestParam(required = false) String status,
                                                           @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(query.submitted(CurrentUser.requireApproverId(), page(page), size(size),
                flowCode, status, keyword));
    }

    @GetMapping("/counts")
    public ApiResponse<WorkbenchCounts> counts() {
        return ApiResponse.ok(query.counts(CurrentUser.requireApproverId()));
    }

    @GetMapping("/cc")
    public ApiResponse<PageResult<CcView>> cc(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int size,
                                              @RequestParam(defaultValue = "false") boolean unreadOnly,
                                              @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(query.cc(CurrentUser.requireApproverId(), page(page), size(size),
                unreadOnly, keyword));
    }

    @PostMapping("/cc/{id}/read")
    public ApiResponse<Void> readCc(@PathVariable String id) {
        query.markCcRead(id, CurrentUser.requireApproverId());
        return ApiResponse.ok();
    }

    @GetMapping("/instances/{instanceId}")
    public ApiResponse<InstanceDetail> detail(@PathVariable String instanceId) {
        return ApiResponse.ok(query.detail(instanceId));
    }

    @PostMapping("/tasks/{taskId}/approve")
    public ApiResponse<ActionResult> approve(@PathVariable String taskId,
                                             @RequestBody(required = false) OpenDtos.HandleRequest request) {
        String comment = request == null ? null : request.comment();
        return ApiResponse.ok(engine.approve(taskId, CurrentUser.requireApproverId(), comment,
                request == null ? null : request.formData()));
    }

    @PostMapping("/tasks/{taskId}/reject")
    public ApiResponse<ActionResult> reject(@PathVariable String taskId,
                                            @RequestBody(required = false) OpenDtos.HandleRequest request) {
        String comment = request == null ? null : request.comment();
        return ApiResponse.ok(engine.reject(taskId, CurrentUser.requireApproverId(), comment));
    }

    private int page(int page) {
        return Math.max(page, 1);
    }

    private int size(int size) {
        return Math.min(Math.max(size, 1), 100);
    }
}
