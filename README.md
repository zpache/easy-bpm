# easy-bpm

独立部署的审批流中台。ERP、SaaS、内部系统通过一组 OpenAPI 接入，即可获得「发起审批 / 待办 / 已办 / 已提交 / 同意 / 驳回」能力，无需自建流程引擎。

独立 Git 仓库，可独立部署，与业务系统零耦合。

## 设计理念：业务表单零设计

**easy-bpm 不做表单设计器，这是刻意的架构选择。**

审批表单本来就长在业务系统里——ERP 里填报销单，就在 ERP 的报销页面填。市面上多数 BPM 产品要求先在流程平台里"画"一遍表单，业务字段一变更就要两边同步，表单定义反而成了接入方的负担。

easy-bpm 的做法：

- **表单数据归业务系统所有**：发起审批时通过 OpenAPI 直接提交 `formData`（自由 JSON），BPM 照单全收，业务表单怎么改都不需要动流程平台；
- **BPM 只管流转，不管表单形状**：存数据、跑流程、留时间线，不定义字段；
- **可选字段元数据（formSchema）**：仅用于提交时 required/type 校验和详情页可读渲染，不参与流程执行，改字段无需重新发布流程版本；
- **收益**：业务表单频繁变更时，接入方零成本跟进，BPM 侧无任何同步动作。

一句话：**表单的"形状"归业务系统，easy-bpm 只负责把审批流跑对、跑快、留痕。**

## 技术栈

| 层次 | 选型 |
|---|---|
| 运行时 | Java 25（Corretto 25.0.5） |
| 框架 | Spring Boot 4.1.2 / Spring Framework 7 |
| 流程引擎 | Flowable 8.0.0（官方支持 Spring Boot 4.x） |
| 数据库 | MySQL 8.4（开发/生产），H2 仅用于单元测试 |
| 持久层 | MyBatis-Plus 3.5.18（多租户插件） |
| 文档 | springdoc-openapi 3.2.0 |
| 认证 | OpenAPI：AppKey + HMAC 签名；控制台：JWT（HS256） |

## 目录结构

```
easy-bpm/
├─ server/                       # Spring Boot 服务
│  ├─ src/main/java/com/easybpm/
│  │  ├─ auth/                   # 控制台登录、OpenAPI 鉴权过滤器
│  │  ├─ tenant/                 # 租户上下文、租户与凭证
│  │  ├─ org/                    # 组织、角色（审批人解析数据源）
│  │  ├─ flow/                   # 审批链模型、JSON→BPMN 编译器、定义管理
│  │  ├─ engine/                 # 引擎门面、审批人解析、查询、抄送
│  │  ├─ open/                   # /open/v1 对外开放接口
│  │  ├─ console/                # /api 控制台接口
│  │  └─ bootstrap/              # 演示数据
│  └─ src/main/resources/db/schema-mysql.sql
├─ db/mysql/                     # 生产建表脚本
├─ console/                      # 控制台前端（Vue3 + TS + Vite + Element Plus + Pinia）
└─ docs/                         # 架构与接入文档
```

## 快速启动

前置：Java 25、Maven 3.6+、MySQL 8。

```bash
# 1. 建库
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS easy_bpm DEFAULT CHARSET utf8mb4"

# 2. 本地启动（推荐）：使用 local 配置（不入库）
#    先复制模板并填入本机数据库密码：
cp server/src/main/resources/application-local.yml.example server/src/main/resources/application-local.yml
cd server
mvn spring-boot:run -Dspring-boot.run.profiles=local

# 或使用 dev 配置（数据库密码通过环境变量提供）
export EASY_BPM_DB_PASSWORD='你的数据库密码'
mvn spring-boot:run

# 打包运行
mvn -DskipTests package
java -jar target/easy-bpm-server-1.0.0.jar --spring.profiles.active=local
```

启动后：

- 服务地址：`http://localhost:8090`
- Swagger UI：`http://localhost:8090/swagger-ui.html`
- 健康检查：`http://localhost:8090/actuator/health`
- 控制台前端（可选）：`cd console && pnpm install && pnpm dev`，访问 `http://localhost:8091`（开发代理已指向 8090）

首次启动会自动建业务表、Flowable 引擎表，并写入演示数据（`easybpm.demo.enabled=true`）：

| 项 | 值 |
|---|---|
| 租户编码 | `demo` |
| 控制台账号 | `admin / admin123`（李四 `lisi`、王五 `wangwu`，密码同） |
| 平台管理员 | 租户编码 `platform`，账号 `platadmin / admin123`（角色 PLATFORM_ADMIN） |
| AppKey / AppSecret | `ak_demo` / `sk_demo_2026` |
| 演示流程 | `leave` 请假审批、`expense` 费用报销（金额 > 5000 加财务审批） |

## 接口一览

OpenAPI（`/open/v1`，AppKey 鉴权）：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/flows` | 已发布流程列表（含表单 schema） |
| POST | `/instances` | 创建并提交审批 |
| GET | `/instances/{instanceId}` | 详情 + 时间线 + 当前待办 |
| GET | `/instances/submitted?userId=` | 我提交的 |
| GET | `/tasks/todo?userId=` | 待办 |
| GET | `/tasks/done?userId=` | 已办 |
| GET | `/tasks/count?userId=` | 三种状态数量 |
| POST | `/tasks/{taskId}/approve` | 同意 |
| POST | `/tasks/{taskId}/reject` | 驳回（TO_END 结束 / TO_START 退回发起人） |
| POST | `/tasks/{taskId}/transfer` | 转交给其他在职人员 |
| GET | `/cc?userId=` | 抄送我的（可按未读过滤、按标题搜索） |
| POST | `/cc/{id}/read` | 标记抄送已读（仅本人，幂等） |
| POST | `/instances/{instanceId}/resubmit` | TO_START 驳回后重新提交 |
| POST | `/instances/{instanceId}/withdraw` | 发起人撤回（CANCELED） |
| POST | `/org/sync` | 组织同步（人员/部门/角色批量 upsert） |

控制台（`/api`，Bearer Token）：

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/auth/login` | 租户编码 + 账号 + 密码登录 |
| GET | `/auth/me` | 当前登录信息 |
| GET/POST | `/flows` | 流程定义列表 / 保存 |
| POST | `/flows/{code}/publish` | 发布（编译 BPMN 并部署） |
| POST | `/flows/{code}/disable` | 停用 |
| DELETE | `/flows/{code}` | 删除从未发布过的草稿 |
| GET | `/flows/{code}/versions` | 版本列表 |
| POST | `/flows/{code}/versions/{version}/rollback` | 回滚到历史版本（立即发布为新版本） |
| GET | `/workbench/todo\|done\|submitted` | 工作台三种列表 |
| GET | `/workbench/cc` | 抄送我的（支持 `unreadOnly` 未读过滤） |
| POST | `/workbench/cc/{id}/read` | 标记抄送已读 |
| GET | `/workbench/counts` | 待办/已办/已提交/未读抄送数量 |
| POST | `/workbench/tasks/{taskId}/approve\|reject` | 控制台审批（身份取登录账号绑定人员） |
| GET | `/org/depts\|users\|roles` | 组织数据查询（人员分页支持关键字/部门/状态过滤） |
| GET | `/org/roles/{roleCode}/users` | 角色授权人员编码列表 |
| POST | `/org/depts` | 管理员新增/编辑部门（按 deptCode 幂等 upsert，与 /org/sync 语义一致） |
| DELETE | `/org/depts/{deptCode}` | 删除部门（有下级或有人员时拒绝） |
| POST | `/org/users` | 管理员新增/编辑人员（编辑时角色列表全量替换） |
| DELETE | `/org/users/{userCode}` | 删除人员（被他人设为主管或任一部门主管时拒绝，角色同步清除） |
| POST | `/org/roles/{roleCode}/assign` | 角色人员全量分配（diff 增删映射，实时影响审批人解析） |
| GET | `/instances` | 管理员视角：本租户全部实例分页（flowCode/status/keyword 过滤，keyword 匹配标题或发起人） |
| GET | `/dashboard/stats` | 管理员数据看板：通过率、平均审批时长、近 14 天发起/办结趋势、流程 TOP5 |

时间序列化：`LocalDateTime` 全局输出 `yyyy-MM-dd HH:mm:ss`（JacksonConfig，Jackson 3 + Boot 4.1）；反序列化兼容 `T` 与空格分隔。

主管继承规则：「直属主管」类审批节点解析时，人员显式设置的 leaderCode 优先；未设置时自动继承所在部门的主管（本部门未设则沿上级部门逐级向上查找第一个配置了主管的部门），部门主管是本人时跳过本级继续向上，避免自审批。仅跨部门汇报线需要显式指定直属主管。
| GET | `/tenant/info` | 当前租户信息 |
| GET/POST | `/tenant/credentials` | 凭证列表（Secret 掩码）/ 新建（Secret 仅此一次完整返回） |
| GET/POST | `/platform/tenants` | 平台管理员：租户列表（含账号/凭证/流程/实例统计）/ 新建租户（可带初始管理员） |
| POST | `/platform/tenants/{id}` | 编辑租户名称与备注 |
| POST | `/platform/tenants/{id}/disable\|enable` | 停用 / 启用租户（停用后该租户控制台登录与 OpenAPI 全部拒绝） |
| POST | `/platform/tenants/{id}/switch` | 平台管理员切换进入目标租户（签发临时 token，前端可一键返回平台视角） |
| POST | `/tenant/credentials/{id}/rotate` | 轮换 AppSecret（旧密钥立即失效，新密钥仅此一次返回） |
| POST | `/tenant/credentials/{id}/disable` | 停用凭证 |
| GET | `/dashboard/summary` | 首页统计 |

## P1 已验证场景

以下均在本地 MySQL + Flowable 8 + Boot 4.1.2 实测通过：

1. 控制台登录，返回 JWT。
2. 发起请假审批，审批人按「发起人直属主管」解析为李四（u1002）。
3. 李四待办可见；同意后流转到人事审批，待办人为赵六（u1004）。
4. 赵六同意后实例状态 `APPROVED`，时间线完整（提交/两次审批及意见）。
5. 已提交、已办列表返回正确。
6. 报销 3000 元：条件 `amount > 5000` 为 false，财务节点被跳过。
7. 报销 8000 元：条件为 true，主管同意后进入财务待办；驳回则实例 `REJECTED`。
8. `idempotencyKey` 重复发起返回同一实例。
9. 错误 AppSecret / 缺少凭证返回 401。
10. 新建第二个租户凭证后，查询租户 1 的实例返回 404，待办数为 0（跨租户隔离生效）。

## 关键设计

### 1. 审批链 JSON → BPMN

租户只维护审批链 JSON（节点顺序、审批人来源、或签/会签、条件、抄送），发布时由 `BpmnCompiler` 编译为 BPMN 2.0 并部署到 Flowable：

- 审批节点 → 多实例 `userTask`，`flowable:collection="${ap_<nodeId>}"`，天然支持或签（`nrOfCompletedInstances >= 1`）与会签；
- 节点后置排他网关，`decision_<nodeId> = REJECT` 走 `endRejected`；
- 带条件节点前置排他网关，`cond_<nodeId>` 在发起时求值，false 直接跳过；
- 抄送节点 → `serviceTask` + `ccDelegate`，落抄送记录。

这样租户侧配置简单，执行侧仍是标准 BPMN，可随时导出/替换引擎。

### 2. 多租户隔离（三道防线）

1. **入口**：OpenAPI 按 AppKey 解析租户；控制台按 JWT 中的 `tid` 解析。租户 ID 只来自凭证，不接受请求参数传入。
2. **数据**：MyBatis-Plus `TenantLineInnerInterceptor` 自动给所有业务表 SQL 追加 `tenant_id = ?`；仅 `bpm_tenant` 与 `bpm_tenant_credential` 例外（认证前置）。
3. **引擎**：部署、启动、任务查询、历史查询全部显式带 `tenantId`，Flowable 自身按 `TENANT_ID_` 隔离。

### 3. 业务表单零设计

- `formData` 为自由 JSON，由接入方在发起时提交，BPM 原样存储并随实例返回；
- `formSchema` 可选，仅描述字段 key/label/type/required，用于提交校验与详情页渲染，不参与流程编译与执行；
- 业务字段变更不需要在 BPM 侧做任何同步，也不影响已发布流程版本与运行中实例。

### 4. 状态与数据

- Flowable 负责状态机；`bpm_instance` 是实例投影，承担列表检索、标题/业务主键映射与统计，避免复杂 join。
- `bpm_task_record` 是审计时间线，与引擎数据解耦，便于合规留痕。
- 审批链版本存 `bpm_flow_version`，发布即新版本，运行中实例不受影响；回滚 = 取历史版本内容覆盖后发布为 N+1 新版本。
- 实例对外 ID 恒定：`bpm_instance.instance_id` 创建后不变；`engine_instance_id` 记录当前引擎实例，TO_START 重提时更换。

### 5. Webhook 回调（outbox）

- 实例结束（通过/驳回/撤回）时在业务事务内写 `bpm_webhook_log` PENDING 行，后台每 30s 轮询投递；
- 失败按 1m/5m/15m/60m 指数退避，默认 5 次后标记 FAILED；请求带 `X-EasyBpm-Signature`（HMAC-SHA256，密钥为租户 `webhook_secret`）；
- 回调地址：发起时 `callbackUrl` 优先，回退租户 `webhook_url`。详见 `docs/deploy.md` 3.2。

### 6. 限流与审计

- OpenAPI 限流在鉴权前拦截（`/open/*`），固定窗口 60s 按 AppKey 计数，默认 600 次/分钟，超限返回 429 + `42900`；
- 控制台敏感操作（流程/凭证写操作）自动落 `bpm_audit_log`（含操作人、目标、traceId），控制台「审计日志」页可查。

### 7. 抄送闭环

- 抄送节点执行时写 `bpm_cc`（接收人、节点、已读标记），同时落一条 `CC` 时间线；
- 被抄送人通过 `/open/v1/cc` 或控制台「抄送我的」查看，支持未读过滤与标题搜索，标记已读仅限本人；
- `counts` 接口返回 `ccUnread`，接入方可在自己的消息中心做红点。

## 阶段计划

| 阶段 | 内容 | 状态 |
|---|---|---|
| P0 | Flowable 8 + Boot 4.1.2 + Java 25 技术验证 | ✅ |
| P1 | 多租户鉴权 + 流程发布 + 发起/待办/同意/驳回纵切 | ✅ |
| P2 | 驳回到发起人（TO_START）、撤回、转交、重提、组织同步接口、索引与幂等顺序修复 | ✅ |
| P3 后端 | 版本回滚、草稿删除、表单 schema 校验（required/type） | ✅ 后端 |
| P5 阶段一 | 控制台前端：登录、布局、仪表盘、流程列表/发布/停用/删除/版本回滚、审批链设计器（节点树+属性抽屉）、工作台待办/已办/我发起的、审批详情时间线 | ✅ |
| P5 阶段二 | 设计器增强（抄送节点）、组织管理页、租户凭证页、详情页表单只读渲染 | ✅ |
| P4 | Webhook 回调（outbox + 指数退避重试）、OpenAPI 限流、控制台操作审计 | ✅ |
| P6 | Docker Compose、部署与运维文档（docs/deploy.md） | ✅ |
| P7 | 抄送闭环（抄送我的查询/已读）+ 核心链路集成测试基线 | ✅ |

## 测试

```bash
cd server && mvn test
```

- 集成测试使用 H2 内存库（原生模式，Flowable 自动建 `ACT_*` 表）+ 真实引擎，无需外部依赖；
- 测试 DDL 见 `server/src/test/resources/db/schema-h2.sql`，配置见 `application-test.yml`；
- 覆盖：发起→同意、驳回 TO_END、驳回 TO_START→重提（ID 恒定）、撤回、幂等键、租户隔离、抄送闭环与越权、审批人为空 AUTO_PASS 兜底；
- 每个用例使用独立租户，互不污染。

## 注意事项

- **H2 不能使用 `MODE=MySQL`**：Flowable 会据此选择 MySQL 方言，`IDENTITY` 建表语句在 H2 下报错。开发用 MySQL，测试用 H2 原生模式。
- Flowable 引擎表（`ACT_*`）与业务表同库，生产环境建议 `flowable.database-schema-update=false`，用官方脚本初始化。
- 生产必须替换 `EASY_BPM_JWT_SECRET`，并通过环境变量提供数据库账号密码。
- 当前驳回策略支持 `TO_END`（直接结束）与 `TO_START`（退回发起人，重提后重新流转）。
- **【待办】审批人运行期兜底**：审批人列表在发起时解析冻结，实例运行中人员离职/停用后，其名下待办会卡死。规划方案：① `/org/sync` 检测 ACTIVE→停用事件，对名下运行中待办按节点最新组织数据重解析并自动转办（时间线留痕），同时刷新集合变量覆盖顺序节点后续任务；② 重解析为空时按节点兜底策略自动通过/驳回，默认拦截策略记告警；③ 审批接口校验操作人在职状态，离职者无法处理存量待办。转办目标建议优先同节点在职审批人，其次部门主管，避免会签计数失衡。
