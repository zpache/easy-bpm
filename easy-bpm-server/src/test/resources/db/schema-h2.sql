-- easy-bpm 业务表（H2，仅测试使用）
-- 由 schema-mysql.sql 等价翻译：去掉 MySQL 专有语法（ENGINE/CHARSET/COMMENT/ON UPDATE/KEY 内联索引），
-- TEXT 改 CLOB，DATETIME(3) 改 TIMESTAMP(3)。Flowable 引擎表（ACT_*）由引擎自动创建。

CREATE TABLE IF NOT EXISTS bpm_tenant (
    id             VARCHAR(32)  NOT NULL,
    code           VARCHAR(64)  NOT NULL,
    name           VARCHAR(128) NOT NULL,
    status         VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    webhook_url    VARCHAR(512) NULL,
    webhook_secret VARCHAR(64)  NULL,
    remark         VARCHAR(500) NULL,
    created_at     TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_tenant_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS bpm_tenant_credential (
    id           VARCHAR(32)  NOT NULL,
    tenant_id    VARCHAR(32)  NOT NULL,
    name         VARCHAR(128) NOT NULL,
    app_key      VARCHAR(64)  NOT NULL,
    app_secret   VARCHAR(128) NOT NULL,
    status       VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    expire_at    TIMESTAMP(3) NULL,
    last_used_at TIMESTAMP(3) NULL,
    created_at   TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_credential_app_key UNIQUE (app_key)
);

CREATE TABLE IF NOT EXISTS bpm_console_user (
    id               VARCHAR(32)  NOT NULL,
    tenant_id        VARCHAR(32)  NOT NULL,
    username         VARCHAR(64)  NOT NULL,
    password_hash    VARCHAR(256) NOT NULL,
    auth_source      VARCHAR(16)  NOT NULL DEFAULT 'LOCAL',
    display_name     VARCHAR(64)  NOT NULL,
    external_user_id VARCHAR(64)  NULL,
    role             VARCHAR(32)  NOT NULL DEFAULT 'MEMBER',
    status           VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    last_login_at    TIMESTAMP(3) NULL,
    created_at       TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_console_user UNIQUE (tenant_id, username)
);

CREATE TABLE IF NOT EXISTS bpm_org_dept (
    id          VARCHAR(32)  NOT NULL,
    tenant_id   VARCHAR(32)  NOT NULL,
    dept_code   VARCHAR(64)  NOT NULL,
    name        VARCHAR(128) NOT NULL,
    parent_code VARCHAR(64)  NULL,
    leader_id   VARCHAR(64)  NULL,
    sort_no     INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_org_dept UNIQUE (tenant_id, dept_code)
);

CREATE TABLE IF NOT EXISTS bpm_org_user (
    id          VARCHAR(32)  NOT NULL,
    tenant_id   VARCHAR(32)  NOT NULL,
    user_code   VARCHAR(64)  NOT NULL,
    name        VARCHAR(64)  NOT NULL,
    dept_code   VARCHAR(64)  NULL,
    dept_name   VARCHAR(128) NULL,
    leader_code VARCHAR(64)  NULL,
    title       VARCHAR(64)  NULL,
    mobile      VARCHAR(191) NULL,
    email       VARCHAR(255) NULL,
    mobile_hash VARCHAR(64)  NULL,
    status      VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_org_user UNIQUE (tenant_id, user_code)
);

CREATE TABLE IF NOT EXISTS bpm_org_user_role (
    id         VARCHAR(32) NOT NULL,
    tenant_id  VARCHAR(32) NOT NULL,
    user_code  VARCHAR(64) NOT NULL,
    role_code  VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_org_user_role UNIQUE (tenant_id, user_code, role_code)
);

CREATE TABLE IF NOT EXISTS bpm_flow_definition (
    id                    VARCHAR(32)  NOT NULL,
    tenant_id             VARCHAR(32)  NOT NULL,
    code                  VARCHAR(64)  NOT NULL,
    name                  VARCHAR(128) NOT NULL,
    category              VARCHAR(64)  NULL,
    icon                  VARCHAR(64)  NULL,
    description           VARCHAR(500) NULL,
    form_schema           CLOB         NULL,
    flow_schema           CLOB         NOT NULL,
    bpmn_xml              CLOB         NULL,
    process_definition_id VARCHAR(64)  NULL,
    version               INT          NOT NULL DEFAULT 0,
    status                VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    created_by            VARCHAR(64)  NULL,
    updated_by            VARCHAR(64)  NULL,
    created_at            TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at          TIMESTAMP(3) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_flow_def UNIQUE (tenant_id, code)
);

CREATE TABLE IF NOT EXISTS bpm_flow_version (
    id                    VARCHAR(32) NOT NULL,
    tenant_id             VARCHAR(32) NOT NULL,
    definition_id         VARCHAR(32) NOT NULL,
    version               INT         NOT NULL,
    flow_schema           CLOB        NOT NULL,
    form_schema           CLOB        NULL,
    bpmn_xml              CLOB        NOT NULL,
    process_definition_id VARCHAR(64) NOT NULL,
    published_by          VARCHAR(64) NULL,
    published_at          TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    changelog             VARCHAR(500) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_flow_version UNIQUE (tenant_id, definition_id, version)
);

CREATE TABLE IF NOT EXISTS bpm_instance (
    id                  VARCHAR(32)  NOT NULL,
    tenant_id           VARCHAR(32)  NOT NULL,
    instance_id         VARCHAR(64)  NOT NULL,
    engine_instance_id  VARCHAR(64)  NULL,
    flow_code           VARCHAR(64)  NOT NULL,
    flow_name           VARCHAR(128) NOT NULL,
    flow_version        INT          NOT NULL DEFAULT 1,
    business_key        VARCHAR(128) NULL,
    title               VARCHAR(255) NOT NULL,
    initiator_id        VARCHAR(64)  NOT NULL,
    initiator_name      VARCHAR(64)  NOT NULL,
    initiator_dept_code VARCHAR(64)  NULL,
    form_data           CLOB         NULL,
    status              VARCHAR(16)  NOT NULL,
    current_node_name   VARCHAR(128) NULL,
    idempotency_key     VARCHAR(191) NULL,
    callback_url        VARCHAR(512) NULL,
    started_at          TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at            TIMESTAMP(3) NULL,
    updated_at          TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_instance_process UNIQUE (tenant_id, instance_id),
    CONSTRAINT uk_instance_idem UNIQUE (tenant_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS bpm_task_record (
    id            VARCHAR(32)  NOT NULL,
    tenant_id     VARCHAR(32)  NOT NULL,
    instance_id   VARCHAR(64)  NOT NULL,
    task_id       VARCHAR(64)  NULL,
    node_id       VARCHAR(64)  NULL,
    node_name     VARCHAR(128) NULL,
    operator_id   VARCHAR(64)  NULL,
    operator_name VARCHAR(64)  NULL,
    action        VARCHAR(32)  NOT NULL,
    comment       VARCHAR(1000) NULL,
    attachments   CLOB         NULL,
    created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS bpm_cc (
    id          VARCHAR(32)  NOT NULL,
    tenant_id   VARCHAR(32)  NOT NULL,
    instance_id VARCHAR(64)  NOT NULL,
    node_id     VARCHAR(64)  NULL,
    node_name   VARCHAR(128) NULL,
    user_id     VARCHAR(64)  NOT NULL,
    user_name   VARCHAR(64)  NULL,
    read_flag   TINYINT      NOT NULL DEFAULT 0,
    created_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS bpm_webhook_log (
    id            VARCHAR(32)  NOT NULL,
    tenant_id     VARCHAR(32)  NOT NULL,
    instance_id   VARCHAR(64)  NOT NULL,
    event_type    VARCHAR(32)  NOT NULL,
    url           VARCHAR(512) NOT NULL,
    payload       CLOB         NULL,
    status        VARCHAR(16)  NOT NULL,
    response_code INT          NULL,
    error         VARCHAR(1000) NULL,
    attempts      INT          NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP(3) NULL,
    created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS bpm_audit_log (
    id            VARCHAR(32)  NOT NULL,
    tenant_id     VARCHAR(32)  NOT NULL,
    operator_id   VARCHAR(64)  NOT NULL,
    operator_name VARCHAR(64)  NULL,
    action        VARCHAR(32)  NOT NULL,
    target_type   VARCHAR(32)  NOT NULL,
    target_id     VARCHAR(128) NOT NULL,
    detail        VARCHAR(1000) NULL,
    trace_id      VARCHAR(64)  NULL,
    created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS bpm_sso_provider (
    id             VARCHAR(32)  NOT NULL,
    tenant_id      VARCHAR(32)  NOT NULL,
    code           VARCHAR(32)  NOT NULL DEFAULT 'default',
    name           VARCHAR(128) NOT NULL,
    protocol       VARCHAR(16)  NOT NULL DEFAULT 'OIDC',
    issuer         VARCHAR(512) NOT NULL,
    client_id      VARCHAR(191) NOT NULL,
    client_secret  VARCHAR(512) NOT NULL,
    scopes         VARCHAR(255) NOT NULL DEFAULT 'openid profile email',
    redirect_uri   VARCHAR(512) NULL,
    name_claim     VARCHAR(64)  NULL,
    email_claim    VARCHAR(64)  NULL,
    status         VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    auto_provision TINYINT      NOT NULL DEFAULT 0,
    default_role   VARCHAR(32)  NOT NULL DEFAULT 'MEMBER',
    created_at     TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_sso_provider UNIQUE (tenant_id, code)
);

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
    last_attempt_at  TIMESTAMP(3) NULL,
    last_login_at    TIMESTAMP(3) NULL,
    created_at       TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_sso_identity UNIQUE (provider_id, subject)
);
