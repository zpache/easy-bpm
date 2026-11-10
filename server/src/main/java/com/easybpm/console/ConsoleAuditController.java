package com.easybpm.console;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.easybpm.audit.BpmAuditLog;
import com.easybpm.audit.BpmAuditLogMapper;
import com.easybpm.auth.CurrentUser;
import com.easybpm.common.ApiResponse;
import com.easybpm.common.BizException;
import com.easybpm.common.ErrorCode;
import com.easybpm.common.PageResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 控制台操作审计查询（仅租户管理员）。
 */
@RestController
@RequestMapping("/api/audit")
public class ConsoleAuditController {

    private final BpmAuditLogMapper auditMapper;

    public ConsoleAuditController(BpmAuditLogMapper auditMapper) {
        this.auditMapper = auditMapper;
    }

    @GetMapping("/logs")
    public ApiResponse<PageResult<AuditView>> logs(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size,
                                                   @RequestParam(required = false) String action,
                                                   @RequestParam(required = false) String keyword) {
        if (!CurrentUser.require().isTenantAdmin()) {
            throw new BizException(ErrorCode.FORBIDDEN, "需要租户管理员权限");
        }
        LambdaQueryWrapper<BpmAuditLog> wrapper = new LambdaQueryWrapper<BpmAuditLog>()
                .eq(action != null && !action.isBlank(), BpmAuditLog::getAction, action)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(BpmAuditLog::getTargetId, keyword)
                        .or().like(BpmAuditLog::getOperatorName, keyword)
                        .or().like(BpmAuditLog::getDetail, keyword))
                .orderByDesc(BpmAuditLog::getCreatedAt);
        Page<BpmAuditLog> result = auditMapper.selectPage(new Page<>(Math.max(page, 1),
                Math.min(Math.max(size, 1), 100)), wrapper);
        List<AuditView> views = result.getRecords().stream()
                .map(a -> new AuditView(a.getAction(), a.getTargetType(), a.getTargetId(),
                        a.getOperatorId(), a.getOperatorName(), a.getDetail(), a.getTraceId(), a.getCreatedAt()))
                .toList();
        return ApiResponse.ok(PageResult.of(views, result.getTotal(), page, size));
    }

    public record AuditView(String action, String targetType, String targetId, String operatorId,
                            String operatorName, String detail, String traceId, LocalDateTime createdAt) {
    }
}
