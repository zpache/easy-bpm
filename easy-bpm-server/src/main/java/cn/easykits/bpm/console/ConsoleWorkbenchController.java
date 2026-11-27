package cn.easykits.bpm.console;

import cn.easykits.bpm.auth.CurrentUser;
import cn.easykits.bpm.common.ApiResponse;
import cn.easykits.bpm.common.BizException;
import cn.easykits.bpm.common.ErrorCode;
import cn.easykits.bpm.common.PageResult;
import cn.easykits.bpm.engine.CcView;
import cn.easykits.bpm.engine.InstanceDetail;
import cn.easykits.bpm.engine.InstanceView;
import cn.easykits.bpm.engine.TaskView;
import cn.easykits.bpm.engine.WorkbenchCounts;
import cn.easykits.bpm.engine.WorkbenchQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 控制台工作台（管理视角）。
 *
 * <p>面向租户管理员，展示当前租户的全量待办/已办/发起/抄送，不绑定任何个人审批身份。
 * 审批动作由接入方业务系统通过 OpenAPI 以真实 userId 完成，控制台仅供查看与跟踪，
 * 故此处不提供 approve/reject 接口，杜绝管理员代审批导致的留痕失真。
 */
@RestController
@RequestMapping("/workbench")
public class ConsoleWorkbenchController {

    private final WorkbenchQueryService query;

    public ConsoleWorkbenchController(WorkbenchQueryService query) {
        this.query = query;
    }

    @GetMapping("/todo")
    public ApiResponse<PageResult<TaskView>> todo(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size,
                                                  @RequestParam(required = false) String flowCode,
                                                  @RequestParam(required = false) String keyword) {
        requireTenantAdmin();
        return ApiResponse.ok(query.todoByTenant(page(page), size(size), flowCode, keyword));
    }

    @GetMapping("/done")
    public ApiResponse<PageResult<TaskView>> done(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size,
                                                  @RequestParam(required = false) String flowCode,
                                                  @RequestParam(required = false) String keyword) {
        requireTenantAdmin();
        return ApiResponse.ok(query.doneByTenant(page(page), size(size), flowCode, keyword));
    }

    @GetMapping("/submitted")
    public ApiResponse<PageResult<InstanceView>> submitted(@RequestParam(defaultValue = "1") int page,
                                                           @RequestParam(defaultValue = "20") int size,
                                                           @RequestParam(required = false) String flowCode,
                                                           @RequestParam(required = false) String status,
                                                           @RequestParam(required = false) String keyword) {
        requireTenantAdmin();
        return ApiResponse.ok(query.allInstances(page(page), size(size), flowCode, status, keyword));
    }

    @GetMapping("/counts")
    public ApiResponse<WorkbenchCounts> counts() {
        requireTenantAdmin();
        return ApiResponse.ok(query.countsByTenant());
    }

    @GetMapping("/cc")
    public ApiResponse<PageResult<CcView>> cc(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int size,
                                              @RequestParam(defaultValue = "false") boolean unreadOnly,
                                              @RequestParam(required = false) String keyword) {
        requireTenantAdmin();
        return ApiResponse.ok(query.ccByTenant(page(page), size(size), unreadOnly, keyword));
    }

    @PostMapping("/cc/{id}/read")
    public ApiResponse<Void> readCc(@PathVariable String id) {
        requireTenantAdmin();
        query.markCcReadByTenant(id);
        return ApiResponse.ok();
    }

    @GetMapping("/instances/{instanceId}")
    public ApiResponse<InstanceDetail> detail(@PathVariable String instanceId) {
        requireTenantAdmin();
        return ApiResponse.ok(query.detail(instanceId));
    }

    private void requireTenantAdmin() {
        if (!CurrentUser.require().isTenantAdmin()) {
            throw new BizException(ErrorCode.FORBIDDEN, "工作台仅租户管理员可访问");
        }
    }

    private int page(int page) {
        return Math.max(page, 1);
    }

    private int size(int size) {
        return Math.min(Math.max(size, 1), 100);
    }
}
