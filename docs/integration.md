# easy-bpm 接入指南（P1）

面向 ERP / SaaS 后端开发者。按本文可在 30 分钟内完成「发起审批 → 待办 → 同意/驳回 → 接收结果」闭环。

## 1. 准备

向审批中台管理员申请：

- `appKey`
- `appSecret`
- 租户编码（仅控制台登录需要）

演示环境：`ak_demo` / `sk_demo_2026`，租户 `demo`。

## 2. 鉴权

所有 `/open/v1/**` 请求必须携带：

| Header | 说明 |
|---|---|
| `X-App-Key` | 应用标识 |
| `X-App-Secret` | 明文密钥（快速联调） |
| `X-Timestamp` | 毫秒时间戳（签名模式必填） |
| `X-Signature` | HMAC-SHA256 十六进制（推荐） |

签名串（字段之间用 `\n`）：

```
METHOD + "\n" + requestURI + "\n" + timestamp + "\n" + rawBody
```

`requestURI` 含路径前缀，例如 `/open/v1/instances`；时间戳与服务器相差不得超过 5 分钟。

### Java 示例

```java
String ts = String.valueOf(System.currentTimeMillis());
String payload = "POST\n/open/v1/instances\n" + ts + "\n" + body;
Mac mac = Mac.getInstance("HmacSHA256");
mac.init(new SecretKeySpec(appSecret.getBytes(UTF_8), "HmacSHA256"));
String sign = HexFormat.of().formatHex(mac.doFinal(payload.getBytes(UTF_8)));

HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/open/v1/instances"))
    .header("Content-Type", "application/json")
    .header("X-App-Key", appKey)
    .header("X-Timestamp", ts)
    .header("X-Signature", sign)
    .POST(HttpRequest.BodyPublishers.ofString(body))
    .build();
```

### Node.js 示例

```js
const ts = String(Date.now());
const payload = `POST\n/open/v1/instances\n${ts}\n${body}`;
const sign = crypto.createHmac('sha256', appSecret).update(payload).digest('hex');
```

> 明文模式（`X-App-Secret`）只建议内网或调试使用；生产请用签名模式，避免密钥在每次请求中传输。

## 3. 接口

统一响应：

```json
{ "code": 0, "message": "ok", "data": {}, "traceId": "4d19bbd6044e440f" }
```

`code=0` 成功；其余为业务错误，HTTP 状态码同时生效。出错时把 `traceId` 提供给中台排查。

时间字段：所有 `LocalDateTime` 统一输出 **`yyyy-MM-dd HH:mm:ss`**（服务器本地时区）。请求入参同时兼容 `2026-11-10 08:43:05` 与 ISO `2026-11-10T08:43:05` 两种写法，秒与毫秒可省略，历史接入方无需调整。

### 3.1 查询可用流程

```
GET /open/v1/flows
```

```json
{
  "code": 0,
  "data": [
    {
      "code": "leave",
      "name": "请假审批",
      "version": 1,
      "formSchema": { "fields": [ { "key": "days", "label": "请假天数", "type": "number", "required": true } ] }
    }
  ]
}
```

接入方可用 `formSchema` 动态渲染发起表单。

### 3.2 创建并提交审批

```
POST /open/v1/instances
```

```json
{
  "flowCode": "leave",
  "businessKey": "LEAVE-2026-0001",
  "title": "张三请假 3 天",
  "initiatorId": "u1001",
  "initiatorName": "张三",
  "initiatorDeptCode": "D-SALES",
  "formData": { "days": 3, "reason": "家中有事" },
  "ccUsers": ["u1004"],
  "idempotencyKey": "LEAVE-2026-0001",
  "callbackUrl": "https://erp.example.com/webhook/approval"
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `flowCode` | 是 | 流程编码 |
| `businessKey` | 否 | 接入方业务主键，便于反查 |
| `title` | 否 | 缺省为「流程名 - 发起人」 |
| `initiatorId` / `initiatorName` | 是 | 发起人；`initiatorId` 必须存在于组织数据 |
| `initiatorDeptCode` | 否 | 缺省从组织数据取 |
| `formData` | 否 | 业务表单数据，条件表达式从这里取变量 |
| `ccUsers` | 否 | 追加抄送人 |
| `idempotencyKey` | 否 | 同一租户内唯一，重复请求返回同一实例 |
| `callbackUrl` | 否 | 实例级回调地址（P4 生效） |

返回：

```json
{
  "code": 0,
  "data": {
    "instanceId": "363c8e05-dc18-11f1-9b0b-860ba02831a5",
    "status": "RUNNING",
    "currentNodeName": "直属主管审批",
    "pendingTasks": [
      { "taskId": "363eff1e-...", "nodeName": "直属主管审批", "assigneeId": "u1002", "assigneeName": "李四" }
    ]
  }
}
```

### 3.3 待办 / 已办 / 已提交

> 需要「实时」感知变化（角标、新待办提醒）时，用 3.10 的 `/workbench` + `/changes` 轮询，
> 不需要接 Webhook。本节接口用于列表页与详情页的直接查询。

```
GET /open/v1/tasks/todo?userId=u1002&page=1&size=20&flowCode=leave&keyword=请假
GET /open/v1/tasks/done?userId=u1002&page=1&size=20
GET /open/v1/instances/submitted?userId=u1001&page=1&size=20&status=APPROVED
GET /open/v1/tasks/count?userId=u1002
```

分页响应：

```json
{ "code": 0, "data": { "list": [], "total": 42, "page": 1, "size": 20 } }
```

- `flowCode` 过滤按流程编码；
- `keyword` 匹配实例标题（最多命中 200 条后过滤）；
- `submitted` 的 `status` 可选 `RUNNING / APPROVED / REJECTED / CANCELED`。

### 3.3.1 抄送我的

```
GET  /open/v1/cc?userId=u1002&page=1&size=20&unreadOnly=true&keyword=报销
POST /open/v1/cc/{id}/read?userId=u1002
```

```json
{
  "code": 0,
  "data": {
    "list": [
      {
        "id": "cc_9f3c...",
        "instanceId": "363c8e05-...",
        "nodeName": "知会财务",
        "instanceTitle": "差旅报销 - 张三",
        "flowName": "费用报销",
        "initiatorName": "张三",
        "instanceStatus": "APPROVED",
        "read": false,
        "createdAt": "2026-11-10T12:30:00"
      }
    ],
    "total": 1
  }
}
```

- 抄送节点流转时自动写入，接入方无需额外调用；
- `unreadOnly=true` 只返回未读；标记已读仅限本人（他人调用返回 403），重复调用幂等；
- `/tasks/count` 额外返回 `ccUnread`，可直接用于红点。

### 3.4 同意

```
POST /open/v1/tasks/{taskId}/approve
```

```json
{
  "userId": "u1002",
  "comment": "同意，注意工作交接",
  "formData": { "days": 2 }
}
```

- `userId` 必须等于任务处理人，否则返回 `43002`；
- 或签节点一人同意即流转；会签节点需全部同意；
- 传入 `formData` 会合并进实例表单数据。

### 3.5 驳回

```
POST /open/v1/tasks/{taskId}/reject
```

```json
{ "userId": "u1002", "comment": "发票不合规" }
```

按节点配置的 `rejectStrategy` 生效：

- `TO_END`（默认）：实例直接结束，状态 `REJECTED`，`currentNodeName` 保留驳回节点；
- `TO_START`：退回发起人，实例保持 `RUNNING`、`currentNodeName` 为 `等待发起人重新提交`，
  发起人调用重新提交接口（见 3.7）后从头流转。

### 3.6 重新提交（TO_START 驳回后）

```
POST /open/v1/instances/{instanceId}/resubmit
```

```json
{ "userId": "u1001", "formData": { "days": 5, "reason": "补全信息" }, "comment": "已修改" }
```

- 仅发起人可调用，且实例必须处于 `等待发起人重新提交` 状态；
- `formData` 与原表单合并，审批人与条件按最新表单重新解析；
- 时间线记录 `RESUBMIT`。

### 3.7 撤回与转交

```
POST /open/v1/instances/{instanceId}/withdraw    # 发起人撤回
POST /open/v1/tasks/{taskId}/transfer            # 处理人转交
```

```json
{ "userId": "u1001", "comment": "填错了" }
{ "userId": "u1002", "targetUserId": "u1003", "comment": "出差，请代处理" }
```

- 撤回：仅发起人、仅 `RUNNING` 状态，撤回后实例 `CANCELED`；
- 转交：仅当前处理人可转，目标必须是同租户在职人员，时间线记录 `TRANSFER`。

### 3.8 组织同步

```
POST /open/v1/org/sync
```

```json
{
  "users": [
    { "userCode": "u1001", "name": "张三", "deptCode": "D-SALES", "leaderCode": "u1002", "title": "销售", "status": "ACTIVE" },
    { "userCode": "u1009", "name": "离职员工", "status": "DISABLED" }
  ],
  "depts": [
    { "deptCode": "D-SALES", "name": "销售部", "parentCode": "D-HQ", "leaderId": "u1002" }
  ],
  "roles": [
    { "userCode": "u1003", "roleCodes": ["FINANCE"] }
  ]
}
```

- 全部按编码幂等 upsert；`users[].status` 支持 `ACTIVE / DISABLED`（离职置 DISABLED 即不可再被解析为审批人）；
- `roles` 为全量替换该人员的角色列表；不传对应段落则跳过。

### 3.9 详情与时间线

```
GET /open/v1/instances/{instanceId}
```

返回 `instance`（含 `formData`、状态、当前节点）、`timeline`（提交/同意/驳回/抄送记录）、`pendingTasks`（当前待办及处理人）。

### 3.10 轮询接入：不接 Webhook 也能实时拿待办

绝大多数 ERP / SaaS 不需要「中台反向调你」：只要暴露中台的查询接口，前端定时拉取即可。
本方案零回调配置——不用公网地址、不用处理验签与重试，接入方只维护一个游标。

```
GET /open/v1/workbench?userId=u1002&todoSize=20&ccSize=10   # 首屏快照（角标+待办+未读抄送+游标）
GET /open/v1/changes?userId=u1002&cursor=1762829400000&limit=50  # 增量变更流
GET /open/v1/instances/by-business-key?businessKey=LEAVE-2026-0001 # 用自己的单号查状态
```

#### 3.10.1 工作台快照（一次请求渲染首屏）

```json
{
  "code": 0,
  "data": {
    "counts": { "todo": 2, "done": 3, "submitted": 5, "ccUnread": 1 },
    "todo": [
      {
        "taskId": "5f0c9a...",
        "instanceId": "363c8e05-dc18-11f1-9b0b-860ba02831a5",
        "nodeName": "直属主管审批",
        "instanceTitle": "张三请假 3 天",
        "flowCode": "leave",
        "flowName": "请假审批",
        "initiatorName": "张三",
        "status": "PENDING",
        "createdAt": "2026-11-11 09:30:00"
      }
    ],
    "ccUnread": [
      { "id": "cc_9f3c...", "instanceId": "363c8e05-...", "instanceTitle": "差旅报销 - 张三", "read": false, "createdAt": "2026-11-11 09:31:00" }
    ],
    "cursor": 1762829400000,
    "serverTime": "2026-11-11 09:43:31"
  }
}
```

- `counts` 可直接做角标，无需再调 `/tasks/count`；
- `todoSize=0` 时只取角标，适合后台常驻的轮询；
- `cursor` 是**首次接增量流**的起点，直接透传给 `/changes`，避免回放历史事件。

#### 3.10.2 增量变更流（推荐主用）

首次不传 `cursor`：只返回初始游标与空事件列表（不回放历史），随后带上游标轮询：

```json
{
  "code": 0,
  "data": {
    "cursor": 1762829420000,
    "hasMore": false,
    "events": [
      {
        "eventId": "TASK_CREATED:5f0c9a3e-...",
        "type": "TASK_CREATED",
        "at": "2026-11-11 09:40:12",
        "taskId": "5f0c9a3e-...",
        "instanceId": "363c8e05-...",
        "instanceTitle": "张三请假 3 天",
        "flowCode": "leave",
        "flowName": "请假审批",
        "nodeName": "直属主管审批",
        "action": null,
        "actorId": "u1002",
        "actorName": "李四",
        "instanceStatus": "RUNNING",
        "currentNodeName": "直属主管审批"
      }
    ],
    "serverTime": "2026-11-11 09:43:34"
  }
}
```

事件类型：

| `type` | 触发时机 | 接入方建议动作 |
|---|---|---|
| `TASK_CREATED` | 新任务派给该用户（含会签的其他成员、转交后的新处理人） | 角标 +1、刷新待办、发站内信 |
| `TASK_COMPLETED` | 该用户处理完任务（含或签被他人抢先而自动取消的） | 从待办移除、刷新已办 |
| `CC_CREATED` | 抄送节点写入该用户 | 未读抄送 +1、弹提醒 |
| `INSTANCE_UPDATED` | 该用户**发起**的实例状态/当前节点变化 | 更新「我发起的」状态，终态可完结本地单据 |

游标与去重（重要）：

- `cursor` 是毫秒时间戳，当作**不透明值**原样回传即可；
- 无积压时服务端会回退约 3 秒形成重叠窗口，防止「事件已写、事务未提交」被漏掉，
  因此**同一 `eventId` 可能重复下发**：客户端按 `eventId` 做本地去重（保留 1 天即可）；
- `hasMore=true` 表示积压超过 `limit`，用返回的 `cursor` **立即再拉一次**，不要等下一个周期；
- `limit` 默认 50、上限 200；事件按 `at` 升序。

推荐的轮询节奏：

| 场景 | 接口 | 频率 |
|---|---|---|
| 应用内角标 / 消息中心 | `/workbench`（`todoSize=0`）或 `/changes` | 15–30 秒 |
| 待办 / 已办列表页 | `/tasks/todo`、`/tasks/done` | 打开页面时 + 下拉刷新 |
| 提交后等待结果 | `/changes` 的 `INSTANCE_UPDATED`，或 `/instances/by-business-key` | 30 秒，直到终态 |
| 页面不可见 / 移动端后台 | — | 暂停轮询，回到前台立即拉一次 |

连续多次空结果可退避到 60 秒；收到 `429`（`42900`）按响应头 `Retry-After` 等待，
不要固定高频重试。单 AppKey 默认 600 次/分钟，按「用户数 × 轮询频率」估算即可。

#### 3.10.3 按业务主键反查

```json
{
  "code": 0,
  "data": {
    "instanceId": "363c8e05-...",
    "businessKey": "LEAVE-2026-0001",
    "status": "APPROVED",
    "currentNodeName": "审批通过",
    "endedAt": "2026-11-11 10:02:11"
  }
}
```

- 接入方不必落地 `instanceId`，用自己的单号即可；跨流程重复时取最近发起的一条，可用 `flowCode` 精确限定；
- 未命中返回 `42001`。

#### 3.10.4 和 Webhook 的关系

- 轮询与 Webhook **互不依赖**，只接轮询就是完整方案；
- 若已有 Webhook，可继续用它在实例结束时做异步落库，轮询作为兜底；
- 两者都接时按 `instanceId + status`（Webhook）与 `eventId`（轮询）分别去重，不会重复处理。

## 4. 审批链配置（控制台）

流程定义由控制台维护，保存结构如下：

```json
{
  "nodes": [
    { "id": "start", "type": "START", "name": "发起人" },
    {
      "id": "n1",
      "type": "APPROVAL",
      "name": "直属主管审批",
      "props": { "approverType": "INITIATOR_LEADER", "leaderLevel": 1, "mode": "ANY" }
    },
    {
      "id": "n2",
      "type": "APPROVAL",
      "name": "财务审批",
      "condition": "amount > 5000",
      "props": { "approverType": "ROLE", "roleCodes": ["FINANCE"], "mode": "ALL" }
    },
    { "id": "cc1", "type": "CC", "name": "抄送人事", "props": { "ccUsers": ["u1004"] } },
    { "id": "end", "type": "END", "name": "结束" }
  ]
}
```

### 审批人来源

| `approverType` | 必填字段 | 说明 |
|---|---|---|
| `SPECIFIED` | `approvers` | 指定人员编码列表 |
| `ROLE` | `roleCodes` | 角色下所有人员 |
| `INITIATOR_LEADER` | `leaderLevel` | 发起人第 N 级主管，默认 1 |
| `DEPT_LEADER` | — | 发起人部门主管 |
| `FORM_FIELD` | `approvers: ["字段key"]` | 表单里的人员编码 |
| `SELF` | — | 发起人本人 |

补充说明：

- **`INITIATOR_LEADER` 的主管链**：人员显式设置的直属主管优先；未设置时继承所在部门主管；本部门无主管则沿上级部门逐级向上；部门主管是本人时跳过本级。因此接入方只需同步部门主管（`/org/sync` 的 `depts[].leaderId`），部门内人员即可解析到正确的直属主管，无需逐人维护 `leaderCode`；跨部门汇报线才需单独指定。
- **审批人为空兜底**（节点配置 `emptyStrategy`）：`FAIL` 拦截发起（默认，返回 `NO_APPROVER`）/ `AUTO_PASS` 自动通过并留痕 / `AUTO_REJECT` 自动驳回。
- 审批人解析在一次发起时计算并冻结，之后的组织变动不影响该实例（运行期兜底转办为后续能力）。

### 条件

- 使用 SpEL，仅允许变量读取与比较/布尔运算，禁用类型引用、Bean 引用与方法调用链；
- 在**发起时**求值，结果为 false 则跳过该节点；
- 变量来自 `formData`，另有 `initiatorId`、`initiatorName`、`initiatorDeptCode`。

### 多人模式

- `ANY`：或签，一人通过即流转，其余任务自动取消；
- `ALL`：会签，全部通过才流转；任一人驳回则立即结束。

## 5. 错误码

| code | HTTP | 含义 |
|---|---|---|
| 40000 | 400 | 参数不合法 |
| 40100 | 401 | 未认证 / AppKey 无效 / 密钥过期 |
| 40101 | 401 | 签名失败 / 时间戳超窗 |
| 40300 | 403 | 无权限 |
| 40301 | 403 | 租户已停用 |
| 40400 | 404 | 资源不存在 |
| 41001 | 404 | 流程不存在或未发布 |
| 41002 | 400 | 审批链配置不合法 |
| 42001 | 404 | 审批实例不存在（跨租户访问同样返回此码） |
| 43001 | 404 | 任务不存在 |
| 43002 | 403 | 当前用户不是任务处理人 |
| 43003 | 409 | 任务已被处理 |
| 44001 | 400 | 未匹配到审批人 |

## 6. 重试与幂等建议

- 发起：始终带 `idempotencyKey`（建议用业务单号）。网络超时后原样重试，不会产生重复实例。
- 同意/驳回：任务已处理会返回 `43003`，按成功处理或刷新任务列表，不要盲目重试其他任务。
- 轮询：`cursor` 原样回传，事件按 `eventId` 去重；`hasMore=true` 立即续拉，`429` 按 `Retry-After` 退避。
- 4xx 不要重试（除 429/408）；5xx 可退避重试 2 次并记录 `traceId`。
