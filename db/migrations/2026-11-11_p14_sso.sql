-- P14 升级脚本：控制台 SSO 单点登录（OIDC）
-- 适用于已按旧 schema 初始化的存量库；新库直接执行 schema.sql 即可。
-- 注意：MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，重复执行会报「列/表已存在」，可忽略。

-- 1. 控制台账号支持 SSO 专属账号（不存密码）
ALTER TABLE bpm_console_user
    MODIFY COLUMN password_hash VARCHAR(256) NULL COMMENT 'SSO 专属账号为空';

ALTER TABLE bpm_console_user
    ADD COLUMN auth_source VARCHAR(16) NOT NULL DEFAULT 'LOCAL' COMMENT 'LOCAL/SSO' AFTER password_hash;

-- 2. 租户级 IdP 配置（client_secret 由应用层 AES-256-GCM 加密后写入）
CREATE TABLE IF NOT EXISTS bpm_sso_provider (
    id             VARCHAR(32)  NOT NULL,
    tenant_id      VARCHAR(32)  NOT NULL,
    code           VARCHAR(32)  NOT NULL DEFAULT 'default' COMMENT '租户内提供方编码，M1 固定 default',
    name           VARCHAR(128) NOT NULL COMMENT '登录页展示名称',
    protocol       VARCHAR(16)  NOT NULL DEFAULT 'OIDC',
    issuer         VARCHAR(512) NOT NULL COMMENT 'OIDC issuer',
    client_id      VARCHAR(191) NOT NULL,
    client_secret  VARCHAR(512) NOT NULL COMMENT 'AES-256-GCM 密文',
    scopes         VARCHAR(255) NOT NULL DEFAULT 'openid profile email',
    redirect_uri   VARCHAR(512) NULL COMMENT '空则用 easybpm.sso.default-redirect-uri',
    name_claim     VARCHAR(64)  NULL,
    email_claim    VARCHAR(64)  NULL,
    status         VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    auto_provision TINYINT      NOT NULL DEFAULT 0,
    default_role   VARCHAR(32)  NOT NULL DEFAULT 'MEMBER',
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_sso_provider (tenant_id, code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '租户 SSO 身份提供方';

-- 3. 外部身份与本地账号绑定（console_user_id 为空 = 已认证未绑定，等待管理员开通）
CREATE TABLE IF NOT EXISTS bpm_sso_identity (
    id               VARCHAR(32)  NOT NULL,
    tenant_id        VARCHAR(32)  NOT NULL,
    provider_id      VARCHAR(32)  NOT NULL,
    subject          VARCHAR(191) NOT NULL,
    issuer           VARCHAR(512) NULL,
    console_user_id  VARCHAR(32)  NULL,
    external_user_id VARCHAR(64)  NULL,
    email            VARCHAR(255) NULL,
    display_name     VARCHAR(128) NULL,
    status           VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    last_attempt_at  DATETIME(3)  NULL,
    last_login_at    DATETIME(3)  NULL,
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_sso_identity (provider_id, subject),
    KEY idx_sso_identity_tenant (tenant_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '外部身份与本地账号绑定';

-- 4. 部署前置：IdP 侧回调地址需与 easybpm.sso.default-redirect-uri 完全一致
--    export EASY_BPM_SSO_REDIRECT_URI='https://bpm.example.com/api/auth/sso/callback'
--    export EASY_BPM_CONSOLE_BASE_URL='https://bpm.example.com'
