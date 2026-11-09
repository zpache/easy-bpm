-- easy-bpm 业务表（MySQL 8）
-- 说明：Flowable 引擎表（ACT_*）由引擎自动创建，不在此脚本内。
-- 时间列统一带 DEFAULT CURRENT_TIMESTAMP(3)（updated_at 带 ON UPDATE），
-- 避免手写 SQL 漏列时在宽松模式下产生零值日期毒行（JDBC 读会报 Zero date value prohibited）。

CREATE TABLE IF NOT EXISTS bpm_tenant (
    id            VARCHAR(32)  NOT NULL COMMENT '租户ID',
    code          VARCHAR(64)  NOT NULL COMMENT '租户编码，接入方使用',
    name          VARCHAR(128) NOT NULL COMMENT '租户名称',
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
    webhook_url   VARCHAR(512) NULL COMMENT '审批结果回调地址',
    webhook_secret VARCHAR(64) NULL COMMENT '回调签名密钥',
    remark        VARCHAR(500) NULL,
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '租户';

CREATE TABLE IF NOT EXISTS bpm_tenant_credential (
    id           VARCHAR(32)  NOT NULL COMMENT '凭证ID',
    tenant_id    VARCHAR(32)  NOT NULL COMMENT '租户ID',
    name         VARCHAR(128) NOT NULL COMMENT '凭证名称',
    app_key      VARCHAR(64)  NOT NULL COMMENT 'AppKey',
    app_secret   VARCHAR(128) NOT NULL COMMENT 'AppSecret',
    status       VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
    expire_at    DATETIME(3)  NULL COMMENT '过期时间，空表示不过期',
    last_used_at DATETIME(3)  NULL,
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_credential_app_key (app_key),
    KEY idx_credential_tenant (tenant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '租户接入凭证';

CREATE TABLE IF NOT EXISTS bpm_console_user (
    id               VARCHAR(32)  NOT NULL,
    tenant_id        VARCHAR(32)  NOT NULL,
    username         VARCHAR(64)  NOT NULL,
    password_hash    VARCHAR(256) NOT NULL,
    display_name     VARCHAR(64)  NOT NULL,
    external_user_id VARCHAR(64)  NULL COMMENT '对应组织人员编码，用于待办查询',
    role             VARCHAR(32)  NOT NULL DEFAULT 'MEMBER' COMMENT 'PLATFORM_ADMIN/TENANT_ADMIN/MEMBER',
    status           VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    last_login_at    DATETIME(3)  NULL,
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_console_user (tenant_id, username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '控制台账号';

CREATE TABLE IF NOT EXISTS bpm_org_dept (
    id          VARCHAR(32)  NOT NULL,
    tenant_id   VARCHAR(32)  NOT NULL,
    dept_code   VARCHAR(64)  NOT NULL,
    name        VARCHAR(128) NOT NULL,
    parent_code VARCHAR(64)  NULL,
    leader_id   VARCHAR(64)  NULL COMMENT '部门主管人员编码',
    sort_no     INT          NOT NULL DEFAULT 0,
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_org_dept (tenant_id, dept_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '组织部门';

CREATE TABLE IF NOT EXISTS bpm_org_user (
    id          VARCHAR(32)  NOT NULL,
    tenant_id   VARCHAR(32)  NOT NULL,
    user_code   VARCHAR(64)  NOT NULL COMMENT '人员编码，由接入方维护',
    name        VARCHAR(64)  NOT NULL,
    dept_code   VARCHAR(64)  NULL,
    dept_name   VARCHAR(128) NULL,
    leader_code VARCHAR(64)  NULL COMMENT '直属主管人员编码',
    title       VARCHAR(64)  NULL,
    mobile      VARCHAR(32)  NULL,
    email       VARCHAR(128) NULL,
    status      VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_org_user (tenant_id, user_code),
    KEY idx_org_user_dept (tenant_id, dept_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '组织人员';

CREATE TABLE IF NOT EXISTS bpm_org_user_role (
    id         VARCHAR(32) NOT NULL,
    tenant_id  VARCHAR(32) NOT NULL,
    user_code  VARCHAR(64) NOT NULL,
    role_code  VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_org_user_role (tenant_id, user_code, role_code),
    KEY idx_org_role (tenant_id, role_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '人员角色';

CREATE TABLE IF NOT EXISTS bpm_flow_definition (
    id                    VARCHAR(32)  NOT NULL,
    tenant_id             VARCHAR(32)  NOT NULL,
    code                  VARCHAR(64)  NOT NULL COMMENT '流程编码，对外唯一',
    name                  VARCHAR(128) NOT NULL,
    category              VARCHAR(64)  NULL,
    icon                  VARCHAR(64)  NULL,
    description           VARCHAR(500) NULL,
    form_schema           TEXT         NULL COMMENT '表单字段 JSON',
    flow_schema           TEXT         NOT NULL COMMENT '审批链 JSON',
    bpmn_xml              TEXT         NULL COMMENT '编译后的 BPMN',
    process_definition_id VARCHAR(64)  NULL COMMENT 'Flowable 流程定义ID',
    version               INT          NOT NULL DEFAULT 0,
    status                VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/DISABLED',
    created_by            VARCHAR(64)  NULL,
    updated_by            VARCHAR(64)  NULL,
    created_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    published_at          DATETIME(3)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_flow_def (tenant_id, code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '流程定义';

CREATE TABLE IF NOT EXISTS bpm_flow_version (
    id                    VARCHAR(32) NOT NULL,
    tenant_id             VARCHAR(32) NOT NULL,
    definition_id         VARCHAR(32) NOT NULL,
    version               INT         NOT NULL,
    flow_schema           TEXT        NOT NULL,
    form_schema           TEXT        NULL,
    bpmn_xml              TEXT        NOT NULL,
    process_definition_id VARCHAR(64) NOT NULL,
    published_by          VARCHAR(64) NULL,
    published_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    changelog             VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_flow_version (tenant_id, definition_id, version)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '流程定义版本';

CREATE TABLE IF NOT EXISTS bpm_instance (
    id                 VARCHAR(32)  NOT NULL,
    tenant_id          VARCHAR(32)  NOT NULL,
    instance_id        VARCHAR(64)  NOT NULL COMMENT '对外实例ID，创建后恒定',
    engine_instance_id VARCHAR(64)  NULL COMMENT '当前Flowable引擎实例ID，TO_START重提后更换',
    flow_code          VARCHAR(64)  NOT NULL,
    flow_name          VARCHAR(128) NOT NULL,
    flow_version       INT          NOT NULL DEFAULT 1,
    business_key       VARCHAR(128) NULL COMMENT '接入方业务主键',
    title              VARCHAR(255) NOT NULL,
    initiator_id       VARCHAR(64)  NOT NULL,
    initiator_name     VARCHAR(64)  NOT NULL,
    initiator_dept_code VARCHAR(64) NULL,
    form_data          TEXT         NULL,
    status             VARCHAR(16)  NOT NULL COMMENT 'RUNNING/APPROVED/REJECTED/CANCELED',
    current_node_name  VARCHAR(128) NULL,
    idempotency_key    VARCHAR(191) NULL,
    callback_url       VARCHAR(512) NULL,
    started_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ended_at           DATETIME(3)  NULL,
    updated_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_instance_process (tenant_id, instance_id),
    UNIQUE KEY uk_instance_idem (tenant_id, idempotency_key),
    KEY idx_instance_engine (tenant_id, engine_instance_id),
    KEY idx_instance_initiator (tenant_id, initiator_id, started_at),
    KEY idx_instance_status (tenant_id, status, started_at),
    KEY idx_instance_business (tenant_id, business_key),
    KEY idx_instance_flow (tenant_id, flow_code, started_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '审批实例投影';

CREATE TABLE IF NOT EXISTS bpm_task_record (
    id            VARCHAR(32)  NOT NULL,
    tenant_id     VARCHAR(32)  NOT NULL,
    instance_id   VARCHAR(64)  NOT NULL COMMENT 'Flowable 流程实例ID',
    task_id       VARCHAR(64)  NULL,
    node_id       VARCHAR(64)  NULL,
    node_name     VARCHAR(128) NULL,
    operator_id   VARCHAR(64)  NULL,
    operator_name VARCHAR(64)  NULL,
    action        VARCHAR(32)  NOT NULL COMMENT 'SUBMIT/APPROVE/REJECT/TRANSFER/REVOKE/RESUBMIT/CC',
    comment       VARCHAR(1000) NULL,
    attachments   TEXT         NULL,
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_record_instance (tenant_id, instance_id, created_at),
    KEY idx_record_operator (tenant_id, operator_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '审批操作记录';

CREATE TABLE IF NOT EXISTS bpm_cc (
    id          VARCHAR(32) NOT NULL,
    tenant_id   VARCHAR(32) NOT NULL,
    instance_id VARCHAR(64) NOT NULL,
    node_id     VARCHAR(64) NULL,
    node_name   VARCHAR(128) NULL,
    user_id     VARCHAR(64) NOT NULL,
    user_name   VARCHAR(64) NULL,
    read_flag   TINYINT     NOT NULL DEFAULT 0,
    created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_cc_user (tenant_id, user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '抄送';

CREATE TABLE IF NOT EXISTS bpm_webhook_log (
    id            VARCHAR(32)  NOT NULL,
    tenant_id     VARCHAR(32)  NOT NULL,
    instance_id   VARCHAR(64)  NOT NULL,
    event_type    VARCHAR(32)  NOT NULL,
    url           VARCHAR(512) NOT NULL,
    payload       TEXT         NULL,
    status        VARCHAR(16)  NOT NULL COMMENT 'SUCCESS/FAILED',
    response_code INT          NULL,
    error         VARCHAR(1000) NULL,
    attempts      INT          NOT NULL DEFAULT 0,
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_webhook_instance (tenant_id, instance_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '回调投递日志';
