package com.easybpm.webhook;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 回调投递日志（outbox）。业务事务内写入 PENDING 行，由后台轮询器投递并重试。
 */
@Data
@TableName("bpm_webhook_log")
public class BpmWebhookLog {

    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    private String instanceId;
    private String eventType;
    private String url;
    private String payload;
    /** PENDING / SUCCESS / FAILED */
    private String status;
    private Integer responseCode;
    private String error;
    private Integer attempts;
    private LocalDateTime nextRetryAt;
    private LocalDateTime createdAt;
}
