package com.easybpm.audit;

import com.easybpm.auth.CurrentUser;
import com.easybpm.common.Ids;
import com.easybpm.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 控制台敏感操作审计。写库失败只记日志、不阻断业务（审计是尽力而为的旁路）。
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final BpmAuditLogMapper auditMapper;

    public AuditService(BpmAuditLogMapper auditMapper) {
        this.auditMapper = auditMapper;
    }

    public void record(String action, String targetType, String targetId, String detail) {
        try {
            var principal = CurrentUser.get();
            recordAs(TenantContext.require(),
                    principal == null ? "unknown" : principal.approverId(),
                    principal == null ? null : principal.displayName(),
                    action, targetType, targetId, detail);
        } catch (Exception e) {
            log.warn("审计写入失败 action={} target={}", action, targetId, e);
        }
    }

    /**
     * 显式指定租户与操作人。用于 SSO 回调这类「登录前置」流程：
     * 此时 TenantContext 与 CurrentUser 都为空，无法从上下文推断。
     */
    public void recordAs(String tenantId, String operatorId, String operatorName,
                         String action, String targetType, String targetId, String detail) {
        try {
            BpmAuditLog entry = new BpmAuditLog();
            entry.setId(Ids.id());
            entry.setTenantId(tenantId);
            entry.setOperatorId(operatorId == null || operatorId.isBlank() ? "unknown" : operatorId);
            entry.setOperatorName(operatorName);
            entry.setAction(action);
            entry.setTargetType(targetType);
            entry.setTargetId(targetId);
            entry.setDetail(detail);
            entry.setTraceId(MDC.get("traceId"));
            entry.setCreatedAt(LocalDateTime.now());
            auditMapper.insert(entry);
        } catch (Exception e) {
            log.warn("审计写入失败 action={} target={}", action, targetId, e);
        }
    }
}
