# easy-bpm 架构说明

## 1. 定位

easy-bpm 是**审批能力提供方**，不是业务系统。业务系统（ERP、SaaS、OA）保留自己的单据、页面和权限，只把「谁来审、审到哪、同意还是驳回」交给中台。

```
ERP / SaaS / 内部系统
        │  OpenAPI（AppKey + 签名）
        ▼
┌─────────────────────────────────────────┐
│ easy-bpm                                │
│  ├ 接入层：租户识别、签名校验、限流      │
│  ├ 业务层：定义管理、审批人解析、工作台  │
│  ├ 引擎层：Flowable 8（BPMN 执行）       │
│  └ 投影层：实例列表、审计时间线、回调    │
└─────────────────────────────────────────┘
        │
     MySQL（业务表 + ACT_* 引擎表）
```

## 2. 为什么用 Flowable + 自研业务层

Flowable 8 官方支持 Spring Boot 4.x，BPMN 语义成熟，多实例（或签/会签）、网关、历史查询都能直接用，没必要自研状态机。

但 Flowable 是「引擎」不是「审批产品」，以下能力必须自研：

| 能力 | 原因 |
|---|---|
| 审批人解析 | Flowable 只认 assignee 表达式，不认「发起人上级」「部门主管」「角色」 |
| 审批链 JSON | 让租户用低代码方式配置，而不是写 BPMN XML |
| 多租户入口 | Flowable 的 tenantId 是 API 参数，需要收敛为不可伪造的上下文 |
| 待办/已办视图 | 需要标题、流程名、发起人等展示字段，纯引擎查询会 N+1 |
| 审计时间线 | 合规要求独立留痕，不能只依赖引擎历史表 |
| 集成能力 | 幂等、回调、接入凭证、错误码 |

## 3. 请求链路

以「同意」为例：

```
POST /open/v1/tasks/{id}/approve
  → TraceIdFilter                 生成 traceId
  → OpenApiAuthFilter             校验 AppKey/签名，写入 TenantContext
  → FlowEngineService.approve
      ├ 校验任务归属（taskTenantId + assignee）
      ├ runtimeService.setVariable(decision_<node> = APPROVE)
      ├ taskService.complete(taskId)
      ├ 写 bpm_task_record（审计）
      └ 同步 bpm_instance（状态、当前节点）
  → ApiResponse{code,message,data,traceId}
```

关键点：`decision_<node>` 写在**流程实例作用域**，而不是任务局部变量。否则多实例子执行结束后，网关读不到该变量。

## 4. 多租户隔离

### 4.1 三条防线

1. **入口不可伪造**：`tenantId` 只从凭证/JWT 解析，接口参数里没有租户字段。
2. **SQL 自动加条件**：MyBatis-Plus `TenantLineInnerInterceptor` 对除 `bpm_tenant`、`bpm_tenant_credential`、`bpm_console_user` 外的所有表追加 `tenant_id`。
3. **引擎显式带租户**：部署、启动、任务、历史查询全部传 `tenantId`；跨租户查实例返回 404。

### 4.2 已实测

- 租户 2 的凭证查询租户 1 的实例 → `404 / 42001`；
- 租户 2 查询同一处理人的待办 → `total = 0`。

### 4.3 注意

- `bpm_tenant_credential` 必须绕过租户插件（认证发生在租户上下文建立之前），因此凭证相关查询要显式带 `tenant_id`。
- `bpm_console_user` 同样绕过自动隔离：平台管理员切换租户后，JWT 的 `tid` 是目标租户，鉴权按主键查账号时若自动追加目标租户条件就会查不到记录（表现为「账号不可用」）。代价是该表的隔离从框架兜底降级为**查询点人工约定**——所有查询必须显式带 `tenant_id`（登录按 `tenantId + username` 匹配、新建账号显式赋值、统计显式 eq 过滤），`ConsoleUserMapper` 上有约定注释，新增查询请遵循。
- 新增业务表时必须包含 `tenant_id` 列，否则插件生成的 SQL 会报错。

## 5. 审批链编译

`BpmnCompiler` 将顺序审批链编译为 BPMN：

| 审批链节点 | BPMN 产物 |
|---|---|
| START | `startEvent` |
| APPROVAL | `userTask` + 多实例（collection=`ap_<id>`，元素变量=`a_<id>`） |
| APPROVAL 后的驳回 | `exclusiveGateway`，默认流到 `endRejected` |
| 带 `condition` 的节点 | 前置 `exclusiveGateway`，`cond_<id>` 为 false 时跳过 |
| CC | `serviceTask` + `${ccDelegate}` |
| END | `endEvent` |

或签完成条件：`${nrOfCompletedInstances >= 1}`；
会签完成条件：`${nrOfCompletedInstances >= nrOfInstances || decision_<id> == 'REJECT'}`。

变量命名约定：

| 变量 | 含义 |
|---|---|
| `ap_<nodeId>` | 该节点审批人列表 |
| `a_<nodeId>` | 多实例元素变量（单人） |
| `decision_<nodeId>` | `PENDING / APPROVE / REJECT` |
| `cond_<nodeId>` | 条件求值结果（Boolean） |
| `cc_<nodeId>` | 抄送人列表 |

### 5.1 审批人解析

审批人类型（`ApproverType`）与解析来源：

| 类型 | 解析方式 |
|---|---|
| `SPECIFIED` | 节点上直接配置的人员编码 |
| `ROLE` | 角色下全部人员（`bpm_org_user_role`） |
| `INITIATOR_LEADER` | 沿直属主管链向上取第 N 级 |
| `DEPT_LEADER` | 发起人所属部门的部门主管 |
| `FORM_FIELD` | 从 `formData` 指定字段取人员（支持单值/数组/逗号分隔） |
| `SELF` | 发起人本人 |

解析完成后统一经 `OrgService.filterActive` 过滤掉不存在或已停用的人员，再按节点「审批人为空兜底策略」处理（`FAIL` 拦截发起 / `AUTO_PASS` 自动通过 / `AUTO_REJECT` 自动驳回）。

**直属主管链（`OrgService.findLeader`）**：人员显式 `leaderCode` 优先；未设置时继承所在部门主管；本部门无主管则沿父部门逐级向上；部门主管是本人时跳过本级（避免自审批），部门树遍历带 visited 防环。这样部门主管只需维护一次，部门内人员默认全部继承，仅跨部门汇报线需要给个人单独指定。

解析结果在**发起时**一次性计算并写入流程变量 `ap_<nodeId>`，引擎运行期不再回查组织数据。

## 6. 数据表

| 表 | 作用 |
|---|---|
| `bpm_tenant` | 租户 |
| `bpm_tenant_credential` | 接入凭证（AppKey/Secret） |
| `bpm_console_user` | 控制台账号（可绑定外部人员编码） |
| `bpm_org_dept` / `bpm_org_user` / `bpm_org_user_role` | 组织与角色，审批人解析数据源 |
| `bpm_flow_definition` | 流程定义（JSON + 编译后的 BPMN） |
| `bpm_flow_version` | 发布版本快照 |
| `bpm_instance` | 实例投影（列表/统计用） |
| `bpm_task_record` | 审计时间线 |
| `bpm_cc` | 抄送 |
| `bpm_webhook_log` | 回调投递日志（P4） |
| `bpm_audit_log` | 控制台敏感操作审计（流程/凭证/租户/组织） |
| `bpm_sso_provider` | 租户级 OIDC IdP 配置（client_secret 加密存储） |
| `bpm_sso_identity` | 外部身份与本地账号绑定（空绑定 = PENDING 待开通） |
| `ACT_*` | Flowable 引擎表（自动创建） |

### 6.1 敏感数据加密（P12）

- **范围**：`bpm_org_user.mobile` / `bpm_org_user.email` / `bpm_tenant_credential.app_secret`；
- **方式**：AES-256-GCM 字段级加密（随机 IV + GCM tag），密文带 `enc:v1:` 前缀，由 MyBatis `EncryptedStringTypeHandler` 在读写两侧透明转换；
- **密钥**：`easybpm.security.data-key`（环境变量 `EASY_BPM_DATA_KEY`）经 SHA-256 派生，支持 `data-key-previous` 双密钥轮换；
- **查询能力**：密文不能模糊匹配，手机号改存 `mobile_hash`（带密钥 HMAC-SHA256 盲索引）以支持精确查找；
- **存量升级**：启动时 `SensitiveDataMigration` 用 JdbcTemplate 读取原始值比对，逐行重写为当前密钥密文并补齐盲索引（幂等，未变更行不产生 UPDATE）；
- **未覆盖**：`bpm_instance.form_data` 属于业务表单，可由接入方自行脱敏后提交；如需整体加密，应在投影层增加加解密列并放弃按表单字段的 SQL 检索。

### 6.2 控制台 SSO（P14）

- **协议**：OIDC 授权码 + PKCE，自研 client（JDK HttpClient + Jackson），不引 `spring-security-oauth2-client`，避免与 `ConsoleAuthFilter` 的 SecurityFilterChain 冲突；
- **登录态**：SSO 只替换「身份来源」，成功后仍签发同一套本地 JWT，`ConsoleAuthFilter` 与 `TenantContext` 链路不变；
- **一次性票据**：state（5 分钟，防 CSRF，携带 nonce/PKCE verifier/回跳路径）与 ticket（60 秒、单次消费、换 JWT）存进程内 Map，多副本部署需改 Redis；
- **账号模型**：`bpm_sso_identity` 的 `console_user_id` 为空表示「IdP 已认证、本地未绑定」，管理员绑定后才允许登录；不做自动建号，也不接受 IdP 的角色 claim 授予权限；
- **安全边界**：`bpm_sso_provider` / `bpm_sso_identity` 与 `bpm_console_user` 一样移出租户插件自动隔离（授权发生在租户上下文建立之前），所有查询点显式带 `tenant_id`；
- **未覆盖**：SAML 2.0、SCIM、单点登出（back-channel SLO）、多 IdP 并行。

## 7. 并发与一致性

- 任务处理用「先查后改」会丢并发，P1 依赖 Flowable 的任务完成语义：任务已被处理时抛 `FlowableObjectNotFoundException`，统一映射为 `43003`。
- 发起幂等由 `bpm_instance(tenant_id, idempotency_key)` 唯一索引兜底；P1 先查后插，极端并发下由数据库唯一键拒绝（P2 补捕获转换）。
- 实例投影与引擎在同一事务内更新（同一 DataSource，Spring 事务管理器统一管理）。

## 8. 演进方向

已完成：P0 技术验证 → P1 多租户纵切 → P2 驳回/撤回/转交/重提/组织同步 → P3 版本回滚与表单校验 → P4 Webhook/限流/审计 → P5/P10 控制台（含组织维护、平台管理、实例视图、数据看板）→ P6 容器化 → P7 抄送闭环与集成测试 → P8 平台管理员与租户管理 → P9 管理员实例视图与看板 → P11 实时查询（工作台快照 / 增量变更流 / 业务主键反查）→ P12 敏感数据加密 → P13 Java SDK → P14 控制台 SSO（OIDC + PKCE）→ P15 条件分支（排他网关）与管理员强制转办。

待建设（按优先级）：

- **通知/实时感知**：已用「轮询查询」覆盖实时待办需求（`/workbench`、`/changes`），接入方无需公网回调；Webhook 仅保留实例结束事件，若需推送新待办/超时提醒可按 `task.created`、`task.overdue` 扩展，属于可选增强而非接入必需；
- **审批人运行期兜底（自动重解析）**：管理员强制转办已提供（实例详情「当前待办」→ 转办）；仍缺 `/org/sync` 离职事件触发的批量自动转办与重解析为空时的自动通过/驳回；
- **加签**：审批中临时增补审批人（会签/或签语义需分别定义）；
- Node SDK、Redis 全局限流与分布式会话（多实例部署时，SSO 的 state/ticket 也需迁移）。
