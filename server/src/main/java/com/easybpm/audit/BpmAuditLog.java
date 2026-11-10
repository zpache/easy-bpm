package com.easybpm.audit;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 控制台敏感操作审计。写入走 AuditService，查询走 /api/audit/logs。
 */
@Data
@TableName("bpm_audit_log")
public class BpmAuditLog {

    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    private String operatorId;
    private String operatorName;
    private String action;
    private String targetType;
    private String targetId;
    private String detail;
    private String traceId;
    private LocalDateTime createdAt;
}
