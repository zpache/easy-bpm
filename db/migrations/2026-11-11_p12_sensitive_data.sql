-- P12 升级脚本：敏感数据加密（手机号 / 邮箱 / AppSecret）
-- 适用于已按旧 schema 初始化的存量库；新库直接执行 schema.sql 即可。
-- 注意：MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，重复执行会报「列已存在」，可忽略。
-- 数据加密由应用启动时自动完成（SensitiveDataMigration），本脚本只改结构。

-- 1. 密文比明文长（enc:v1: + Base64(IV+密文+TAG)），加宽列并补手机号盲索引
ALTER TABLE bpm_org_user
    MODIFY COLUMN mobile VARCHAR(191) NULL COMMENT 'AES-256-GCM 密文（enc:v1:...）',
    MODIFY COLUMN email VARCHAR(255) NULL COMMENT 'AES-256-GCM 密文（enc:v1:...）',
    ADD COLUMN mobile_hash VARCHAR(64) NULL COMMENT '手机号盲索引 HMAC-SHA256，仅用于精确查找' AFTER email;

ALTER TABLE bpm_org_user
    ADD KEY idx_org_user_mobile_hash (tenant_id, mobile_hash);

-- 2. AppSecret 落库加密
ALTER TABLE bpm_tenant_credential
    MODIFY COLUMN app_secret VARCHAR(255) NOT NULL COMMENT 'AppSecret（AES-256-GCM 密文）';

-- 3. 部署前置：配置独立数据密钥（未配置时使用内置开发密钥，换环境即无法解密）
--    export EASY_BPM_DATA_KEY='<32 字节以上随机串>'
--    轮换：新密钥配 EASY_BPM_DATA_KEY、旧密钥配 EASY_BPM_DATA_KEY_PREVIOUS，启动后自动重写为新密钥，
--    确认无解密报错后即可删除旧密钥。
