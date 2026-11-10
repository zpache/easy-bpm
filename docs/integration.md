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
- 4xx 不要重试（除 429/408）；5xx 可退避重试 2 次并记录 `traceId`。
