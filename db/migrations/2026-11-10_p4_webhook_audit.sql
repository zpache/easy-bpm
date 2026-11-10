-- P4 升级脚本：Webhook 重试（outbox）+ 控制台操作审计
-- 适用于已按旧 schema 初始化的存量库；新库直接执行 schema.sql 即可

-- 1. bpm_webhook_log 增加重试列与轮询索引（幂等：列已存在时报错可忽略）
ALTER TABLE bpm_webhook_log
    ADD COLUMN next_retry_at DATETIME(3) NULL COMMENT '下次重试时间，PENDING 行按此列轮询' AFTER attempts;
ALTER TABLE bpm_webhook_log
    ADD KEY idx_webhook_retry (status, next_retry_at);

-- 2. 控制台操作审计表
CREATE TABLE IF NOT EXISTS bpm_audit_log (
    id            VARCHAR(32)  NOT NULL,
    tenant_id     VARCHAR(32)  NOT NULL,
    operator_id   VARCHAR(64)  NOT NULL COMMENT '控制台账号或 userCode',
    operator_name VARCHAR(64)  NULL,
    action        VARCHAR(32)  NOT NULL COMMENT 'FLOW_SAVE/FLOW_PUBLISH/FLOW_DISABLE/FLOW_DELETE/FLOW_ROLLBACK/CREDENTIAL_CREATE/CREDENTIAL_ROTATE/CREDENTIAL_DISABLE',
    target_type   VARCHAR(32)  NOT NULL COMMENT 'FLOW/CREDENTIAL',
    target_id     VARCHAR(128) NOT NULL COMMENT '流程编码或凭证ID',
    detail        VARCHAR(1000) NULL,
    trace_id      VARCHAR(64)  NULL,
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_audit_tenant (tenant_id, created_at),
    KEY idx_audit_action (tenant_id, action, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '控制台操作审计';
