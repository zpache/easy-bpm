# easy-bpm 部署指南

## 1. 架构

```
┌─────────────┐      ┌──────────────────┐      ┌────────────┐
│ 控制台 nginx │ ───▶ │ easy-bpm-server  │ ───▶ │ MySQL 8.4  │
│ (静态+反代)  │ /api │  :8090           │      │ 业务表+ACT_ │
└─────────────┘      └──────────────────┘      └────────────┘
       ▲                    ▲
       │ 登录/工作台         │ OpenAPI（AppKey 签名 + 限流）
   运营/审批人员          接入方 ERP / SaaS
```

- 控制台前端是纯静态资源，由 nginx 托管并把 `/api` 反代到服务端；
- 服务端无状态，可水平扩容（限流为单机实现，多实例建议在网关层做全局限流）；
- MySQL 存业务表与 Flowable 引擎表（同库，表前缀区分 `bpm_*` / `ACT_*`）。

## 2. Docker Compose 部署（推荐）

前置：Docker 24+，宿主机有 JDK 25 + Maven（仅构建 jar 用）。

```bash
# 1. 构建后端 jar
cd server && JAVA_HOME=<corretto-25 路径> mvn -DskipTests package && cd ..

# 2. 准备环境变量（推荐写入同目录 .env，权限 600）
cat > .env <<'EOF'
EASY_BPM_DB_PASSWORD=换成强密码
EASY_BPM_JWT_SECRET=换成32位以上随机串
EOF

# 3. 启动
docker compose up -d --build
```

启动后：

| 入口 | 地址 |
|---|---|
| 控制台 | `http://<主机>/` |
| 服务端 / OpenAPI | `http://<主机>:8090/open/v1/...` |
| 健康检查 | `http://<主机>:8090/actuator/health` |

首次启动 mysql 容器会自动执行 `db/mysql/schema.sql` 建业务表；Flowable 引擎表由服务端首次启动创建（prod profile 下 `flowable.database-schema-update=false`，因此**首次启动前需临时放开一次**，见下文 4.3）。

## 3. 配置项

### 3.1 环境变量（docker compose）

| 变量 | 说明 | 默认 |
|---|---|---|
| `EASY_BPM_DB_PASSWORD` | 数据库 root 密码（必填） | - |
| `EASY_BPM_JWT_SECRET` | 控制台 JWT 签名密钥，32 位以上随机串（必填） | - |
| `EASY_BPM_WEBHOOK_ENABLED` | Webhook 回调开关 | `true` |
| `EASY_BPM_RATE_LIMIT` | OpenAPI 限流（次/分钟，按 AppKey，0 关闭） | `600` |

### 3.2 Webhook 回调（P4）

审批实例结束时（通过 / 驳回 / 撤回）回调接入方：

- **回调地址**：发起时传 `callbackUrl` 优先；否则回退租户的 `webhook_url`（由运维直接更新 `bpm_tenant` 表，后续版本开放控制台配置）；
- **请求**：POST JSON，Header 含 `X-EasyBpm-Event`（`instance.completed` / `instance.canceled`）、`X-EasyBpm-Signature`（HMAC-SHA256，密钥为租户 `webhook_secret`）、`X-EasyBpm-Delivery-Id`；
- **可靠性**：outbox 模式——事务内落 `bpm_webhook_log` PENDING 行，后台每 30s 轮询投递；失败按 1m / 5m / 15m / 60m 指数退避重试，默认最多 5 次后标记 FAILED；2xx 视为成功；
- **接入方校验**：用 `webhook_secret` 对原始 body 计算 HMAC-SHA256，与 `X-EasyBpm-Signature` 比对即可防伪造。

### 3.3 限流（P4）

- 位置：`/open/*` 鉴权之前（恶意高频请求在鉴权前拦截），固定窗口 60 秒，按 `X-App-Key`（无凭证时按来源 IP）；
- 超限返回 HTTP 429 + 业务码 `42900`，带 `Retry-After`；
- 单机内存实现，多实例部署时请在网关层（nginx / SLB）配置全局限流。

### 3.4 审计日志（P4）

控制台敏感操作（流程保存/发布/停用/删除/回滚、凭证新建/轮换/停用）自动记录到 `bpm_audit_log`，含操作人、目标、详情与 traceId；控制台「审计日志」页可按操作类型/关键字检索（仅管理员）。

## 4. 运维手册

### 4.1 建库与初始化

```bash
docker exec -i easy-bpm-mysql mysql -uroot -p$EASY_BPM_DB_PASSWORD easy_bpm < db/mysql/schema.sql
```

### 4.2 升级

1. 备份：`docker exec easy-bpm-mysql mysqldump -uroot -p$EASY_BPM_DB_PASSWORD easy_bpm > backup.sql`
2. 执行 `db/migrations/` 下未应用的升级脚本；
3. 重新构建并滚动重启：`docker compose up -d --build server`。

### 4.3 Flowable 引擎表

- prod profile 默认 `flowable.database-schema-update=false`；
- **首次部署**：临时以 `FLOWABLE_DATABASE_SCHEMA_UPDATE=true` 启动一次（或先临时改环境变量 `docker compose run`），建完 `ACT_*` 表后恢复 `false`；
- 升级 Flowable 大版本前参考官方升级脚本，先备份。

### 4.4 生产检查清单

- [ ] `EASY_BPM_JWT_SECRET` 已替换为强随机值，且未入库 git
- [ ] 数据库密码强随机，`.env` 权限 600
- [ ] 演示数据确认关闭（prod profile 默认 `easybpm.demo.enabled=false`）
- [ ] Swagger 仅内网可达（或在 nginx 层封禁 `/swagger-ui`、`/v3/api-docs`）
- [ ] `bpm_tenant` 的 `webhook_secret` 已为每个启用回调的租户设置
- [ ] MySQL 数据卷已纳入备份计划
- [ ] 多实例部署时，全局限流已在网关层配置

## 5. 非容器部署

1. `mvn -DskipTests package` 得到 `server/target/easy-bpm-server-1.0.0.jar`；
2. `java -jar easy-bpm-server-1.0.0.jar --spring.profiles.active=prod`，数据库连接用环境变量注入（见 `application-prod.yml`）；
3. 前端 `cd console && pnpm build`，把 `dist/` 交给任意静态服务器，并将 `/api` 反代到 8090（参考 `console/nginx.conf`）。
