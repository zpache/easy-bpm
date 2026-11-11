# easy-bpm Java SDK

面向 Java 接入方的最小客户端：**只依赖 Jackson + JDK HttpClient**，不含 Web 框架，可直接放进 Spring Boot / 传统 SSM / 定时任务工程。

- 源码：`sdk-java/`（Maven 模块，`com.easybpm:easy-bpm-sdk:1.0.0`，Java 17+）
- 覆盖 OpenAPI 全量接口：发起、同意/驳回/转交、撤回、重提、待办/已办/抄送、工作台快照、增量轮询、组织同步
- 内置签名鉴权、GET 自动重试、429 退避、统一异常（`code` / `traceId` / `httpStatus`）

## 1. 引入

仓库内安装到本地：

```bash
mvn -f sdk-java/pom.xml install
```

业务工程依赖：

```xml
<dependency>
    <groupId>com.easybpm</groupId>
    <artifactId>easy-bpm-sdk</artifactId>
    <version>1.0.0</version>
</dependency>
```

> 只需要 Jackson（`jackson-databind`）与 JDK 17+；HTTP 使用 JDK 自带 `java.net.http.HttpClient`，不引入 OkHttp/Spring。

## 2. 快速开始

```java
EasyBpmClient client = EasyBpmClient.builder()
        .baseUrl("http://localhost:8090")
        .appKey("ak_demo")
        .appSecret("sk_demo_2026")
        .build();

// 1. 查可用流程（含表单 schema）
List<FlowSummary> flows = client.flows();

// 2. 发起审批（建议带 idempotencyKey，超时重试不会重复建单）
StartResult started = client.start(StartRequest.builder()
        .flowCode("leave")
        .businessKey("LEAVE-2026-0001")
        .title("张三请假 3 天")
        .initiator("u1001", "张三")
        .formData(Map.of("days", 3, "reason", "家中有事"))
        .idempotencyKey("LEAVE-2026-0001")
        .build());

// 3. 审批人处理待办
PageResult<TaskView> todo = client.todo("u1002", 1, 20, null, null);
for (TaskView task : todo.list()) {
    client.approve(task.taskId(), "u1002", "同意", null);
}

// 4. 发起人查结果（用自己的单号，不必落地 instanceId）
InstanceView view = client.byBusinessKey("LEAVE-2026-0001");
if ("APPROVED".equals(view.status())) {
    // 完结本地单据
}
```

`EasyBpmClient` 线程安全，整个应用复用一个实例即可（内部 `HttpClient` 自带连接池）。

## 3. 实时待办：增量轮询

不接 Webhook 时，用 `poller` 拉取变更流；SDK 负责游标推进、`hasMore` 立即续拉、`eventId` 去重与失败退避。

```java
WorkbenchSnapshot snapshot = client.workbench("u1002", 20, 10);   // 首屏：角标 + 待办 + 未读抄送

ChangePoller poller = client.poller("u1002", snapshot.cursor())
        .interval(Duration.ofSeconds(15))
        .limit(50)
        .onEvent(event -> {
            switch (event.type()) {
                case "TASK_CREATED" -> notifyNewTodo(event);        // 新待办
                case "TASK_COMPLETED" -> removeTodo(event);         // 已办
                case "CC_CREATED" -> notifyCc(event);               // 抄送
                case "INSTANCE_UPDATED" -> syncInstance(event);     // 我发起的实例状态变化
            }
        })
        .onError(err -> log.warn("easy-bpm 轮询失败，稍后自动重试", err))
        .start();

// 应用关闭时
poller.close();
```

事件类型、轮询节奏与去重语义见 `docs/integration.md` 3.10。自定义间隔时建议角标 15–30 秒、页面不可见时 `poller.pause()`、回到前台 `poller.start()`。

Spring Boot 中注册为 Bean：

```java
@Configuration
class EasyBpmConfig {

    @Bean
    EasyBpmClient easyBpmClient(@Value("${easybpm.base-url}") String baseUrl,
                               @Value("${easybpm.app-key}") String appKey,
                               @Value("${easybpm.app-secret}") String appSecret) {
        return EasyBpmClient.create(baseUrl, appKey, appSecret);
    }

    @Bean(destroyMethod = "close")
    ChangePoller todoPoller(EasyBpmClient client) {
        return client.poller("u1002", null).interval(Duration.ofSeconds(20)).onEvent(this::handle).start();
    }
}
```

## 4. 鉴权与签名

SDK 默认签名模式，无需手工拼 Header：

```
X-App-Key:    appKey
X-Timestamp:  毫秒时间戳
X-Signature:  HMAC-SHA256(appSecret, METHOD + "\n" + 路径含查询串 + "\n" + 时间戳 + "\n" + body)
```

- GET 的查询串参与签名（服务端同时兼容不含查询串的旧写法）；
- 时间戳与服务器相差不得超过 5 分钟，SDK 每次请求现取。

如需网关/自研客户端对齐，参见 `docs/integration.md` 第 2 节。

## 5. 错误处理

所有失败都抛 `EasyBpmException`，字段与接入指南错误码一致：

```java
try {
    client.approve(taskId, userId, "同意", null);
} catch (EasyBpmException e) {
    if (e.isTaskAlreadyHandled()) {          // 43003：已被处理，按成功处理
        return;
    }
    if (e.isAuthError()) {                   // 40100/40101/40300/40301
        log.error("凭证或权限问题：{}", e.getMessage());
    }
    log.error("调用失败 code={} http={} traceId={}", e.code(), e.httpStatus(), e.traceId());
}
```

- **GET 自动重试**：网络异常 / 5xx / 429 最多重试 `maxRetries` 次（默认 2，指数退避，429 遵循 `Retry-After`）；
- **POST 不自动重试**：避免重复处理任务；发起接口靠 `idempotencyKey` 天然幂等，可自行重试。

## 6. 数据约定

- 时间字段均为字符串 `yyyy-MM-dd HH:mm:ss`（服务端统一序列化），需要计算时用 `DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")` 解析；
- `formData` / `formSchema` 为自由 JSON，SDK 以 `Map<String, Object>` / `Object` 承载，不做结构约束；
- 分页参数 `size` 上限 100（待办/已办/我提交的）与 200（变更流），SDK 不额外截断。

## 7. 方法对照

| SDK 方法 | OpenAPI |
|---|---|
| `flows()` | `GET /open/v1/flows` |
| `start(StartRequest)` | `POST /open/v1/instances` |
| `resubmit(instanceId, ...)` | `POST /open/v1/instances/{id}/resubmit` |
| `withdraw(instanceId, ...)` | `POST /open/v1/instances/{id}/withdraw` |
| `detail(instanceId)` | `GET /open/v1/instances/{id}` |
| `byBusinessKey(key[, flowCode])` | `GET /open/v1/instances/by-business-key` |
| `submitted(...)` / `todo(...)` / `done(...)` | `GET /open/v1/instances/submitted`、`/tasks/todo`、`/tasks/done` |
| `counts(userId)` | `GET /open/v1/tasks/count` |
| `workbench(userId, todoSize, ccSize)` | `GET /open/v1/workbench` |
| `changes(userId, cursor, limit)` / `poller(...)` | `GET /open/v1/changes` |
| `cc(...)` / `markCcRead(ccId, userId)` | `GET /open/v1/cc`、`POST /open/v1/cc/{id}/read` |
| `approve(...)` / `reject(...)` / `transfer(...)` | `POST /open/v1/tasks/{taskId}/approve\|reject\|transfer` |
| `orgSync(OrgSyncRequest)` | `POST /open/v1/org/sync` |
