package com.easybpm.console;

import com.easybpm.auth.CurrentUser;
import com.easybpm.common.ApiResponse;
import com.easybpm.common.BizException;
import com.easybpm.common.ErrorCode;
import com.easybpm.common.PageResult;
import com.easybpm.engine.InstanceView;
import com.easybpm.engine.WorkbenchQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员视角的实例查询：本租户全部审批实例（区别于工作台「我发起的」）。
 */
@RestController
@RequestMapping("/api/instances")
public class ConsoleInstanceController {

    private final WorkbenchQueryService query;

    public ConsoleInstanceController(WorkbenchQueryService query) {
        this.query = query;
    }

    @GetMapping
    public ApiResponse<PageResult<InstanceView>> list(@RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size,
                                                      @RequestParam(required = false) String flowCode,
                                                      @RequestParam(required = false) String status,
                                                      @RequestParam(required = false) String keyword) {
        if (!CurrentUser.require().isTenantAdmin()) {
            throw new BizException(ErrorCode.FORBIDDEN, "需要管理员权限");
        }
        return ApiResponse.ok(query.allInstances(Math.max(page, 1), Math.min(Math.max(size, 1), 100),
                flowCode, status, keyword));
    }
}
