-- P18：审批实例记录来源应用（appKey）。
-- 同一租户下多个接入方（appKey）时，用于区分流程来源并支持按应用过滤。
-- 注意：MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，重复执行会报「列已存在」，可忽略。

ALTER TABLE bpm_instance
    ADD COLUMN source_app_key  VARCHAR(64)  NULL COMMENT '来源接入方 appKey（服务端鉴权注入，不接受请求参数）',
    ADD COLUMN source_app_name VARCHAR(128) NULL COMMENT '来源应用名称';

ALTER TABLE bpm_instance
    ADD KEY idx_instance_source (tenant_id, source_app_key, started_at);
