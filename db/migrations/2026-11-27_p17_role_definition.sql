-- P17: 角色定义表 —— 角色从「隐式（仅存在于授权关系）」升级为显式定义
-- 关联处仍以 role_code 为键（授权关系、审批节点、OpenAPI 同步契约不变），role_name 仅作展示。
-- 幂等：可重复执行。

CREATE TABLE IF NOT EXISTS bpm_org_role (
    id         VARCHAR(32)  NOT NULL,
    tenant_id  VARCHAR(32)  NOT NULL,
    role_code  VARCHAR(64)  NOT NULL,
    role_name  VARCHAR(64)  NOT NULL,
    created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_org_role (tenant_id, role_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色定义';
