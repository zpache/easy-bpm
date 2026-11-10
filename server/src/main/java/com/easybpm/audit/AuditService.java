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
            BpmAuditLog entry = new BpmAuditLog();
            entry.setId(Ids.id());
            entry.setTenantId(TenantContext.require());
            var principal = CurrentUser.get();
            entry.setOperatorId(principal == null ? "unknown" : principal.approverId());
            entry.setOperatorName(principal == null ? null : principal.displayName());
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
