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
2. **SQL 自动加条件**：MyBatis-Plus `TenantLineInnerInterceptor` 对除 `bpm_tenant`、`bpm_tenant_credential` 外的所有表追加 `tenant_id`。
3. **引擎显式带租户**：部署、启动、任务、历史查询全部传 `tenantId`；跨租户查实例返回 404。

### 4.2 已实测

- 租户 2 的凭证查询租户 1 的实例 → `404 / 42001`；
- 租户 2 查询同一处理人的待办 → `total = 0`。

### 4.3 注意

- `bpm_tenant_credential` 必须绕过租户插件（认证发生在租户上下文建立之前），因此凭证相关查询要显式带 `tenant_id`。
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
| `ACT_*` | Flowable 引擎表（自动创建） |

## 7. 并发与一致性

- 任务处理用「先查后改」会丢并发，P1 依赖 Flowable 的任务完成语义：任务已被处理时抛 `FlowableObjectNotFoundException`，统一映射为 `43003`。
- 发起幂等由 `bpm_instance(tenant_id, idempotency_key)` 唯一索引兜底；P1 先查后插，极端并发下由数据库唯一键拒绝（P2 补捕获转换）。
- 实例投影与引擎在同一事务内更新（同一 DataSource，Spring 事务管理器统一管理）。

## 8. 演进方向

- P2：`TO_START` 驳回后重提、撤回、转交、加签、组织同步接口；
- P3：定义可视化编辑、版本回滚、表单设计器；
- P4：事务提交后 Webhook（HMAC 签名、指数退避重试）、限流、审计日志；
- P5：Vue3 控制台；
- P6：容器化与运维文档。
