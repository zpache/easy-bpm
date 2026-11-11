# 控制台单点登录（SSO）

控制台支持通过企业 IdP 登录（OIDC 授权码 + PKCE），与本地密码登录并存。

## 1. 能力边界

| 项 | 结论 |
|---|---|
| 协议 | OIDC Authorization Code + PKCE（S256），签名算法支持 RS256/384/512 |
| 作用范围 | **仅控制台人员登录**；接入方系统调 OpenAPI 仍用 AK/SK 签名，两者互不影响 |
| 账号供给 | 默认**拒绝**：IdP 认证通过但本地未绑定时只登记身份，管理员手工绑定后才可登录 |
| 权限 | SSO 只能登录到已存在的控制台账号；IdP 的组/角色 claim 不参与授权，不能授予 `PLATFORM_ADMIN` |
| 多 IdP | 当前一个租户一个 IdP（编码固定 `default`），多 IdP 在后续版本支持 |
| 单点登出 | 未实现 back-channel SLO；控制台退出只清理本地会话 |

## 2. 配置步骤

### 2.1 IdP 侧（以 Keycloak 为例）

1. 建 Realm（如 `acme`），建 OIDC Client（如 `easy-bpm`）；
2. Client authentication 打开（机密客户端），记下 Client ID / Client Secret；
3. Valid redirect URIs 填 `http://localhost:8091/api/auth/sso/callback`（与 `EASY_BPM_SSO_REDIRECT_URI` 完全一致，生产换成 https 域名）；
4. 给用户补 `email`、`name` 属性，`sub` 保持稳定（不要用会变的用户名做 sub）。

本地起一个 Keycloak 联调：

```bash
docker run -p 8080:8080 \
  -e KC_BOOTSTRAP_ADMIN_USERNAME=admin -e KC_BOOTSTRAP_ADMIN_PASSWORD=admin \
  quay.io/keycloak/keycloak:26.0 start-dev
```

issuer 即 `http://localhost:8080/realms/acme`。

### 2.2 平台侧

1. 用平台管理员登录 → 平台管理 → 目标租户行点 **SSO**；
2. 填 Issuer / Client ID / Client Secret，点「测试连接」确认 discovery 可读；
3. 保存配置，启用登录；
4. 登录页输入该租户编码，会出现「使用 xxx 登录」按钮。

配置只开放给平台管理员：`client_secret` 决定「谁能进来」，若租户管理员也能改，可能把认证指向自己的 IdP 完成账号接管。

## 3. 登录时序

```
浏览器            控制台后端                     企业 IdP
  │  GET /api/auth/sso/providers?tenantCode=   │
  │ ──────────────────────────────────────────>│  返回启用的登录方式（仅 code/name）
  │  GET /api/auth/sso/authorize?tenantCode=&provider=&redirect=
  │ ──────────────────────────────────────────>
  │        生成 state + nonce + PKCE verifier（服务端暂存 5 分钟）
  │ <────────── 302 到 authorization_endpoint ──┤
  │ ─────────────── 用户在 IdP 认证 ───────────>│
  │ <── 302 /api/auth/sso/callback?code=&state=┤
  │                                            │  ① state 取出即删（防重放 / CSRF）
  │                                            │  ② code + verifier + secret 换 token
  │                                            │  ③ 验 id_token：签名(JWKS)/iss/aud/exp/nonce
  │                                            │  ④ userinfo 补 name/email（失败不阻断）
  │                                            │  ⑤ 查绑定：无绑定 → 记 PENDING 并拒绝
  │ <── 302 /sso/callback?ticket=一次性票据 ────┤
  │  POST /api/auth/sso/exchange {ticket}      │
  │ ──────────────────────────────────────────>│  签发本地 JWT（60 秒内单次消费）
```

JWT 不进 URL：回调只带一次性 ticket，前端换到 token 后与密码登录完全一致地存本地，
`ConsoleAuthFilter` 无需改动。

## 4. 账号绑定

### 4.1 首次登录

员工第一次点「企业登录」并通过 IdP 认证后，本地没有绑定关系，会被拒绝并提示
「已通过企业身份认证，但尚未开通控制台账号」。同时系统登记一条 `PENDING` 身份记录
（subject / 姓名 / 邮箱 / 最近尝试时间），管理员在「平台管理 → SSO → 企业身份 → 待绑定」
里能看到这个人。

### 4.2 管理员绑定

1. 先在该租户建好控制台账号（组织维护里建人员后，用「租户凭证」页或平台建号流程创建账号）；
2. 在待绑定列表点「绑定」，选择本地账号；
3. 可选「同时清空本地密码，仅允许企业登录」——该账号变成 SSO 专属；
4. 员工再点一次企业登录即可进入。

约束：

- 一个控制台账号在同一 IdP 下只能绑定一个企业身份，反之亦然，冲突会提示先解绑；
- 平台管理员账号禁止设为 SSO 专属，避免 IdP 故障时无法进入平台管理；
- 解绑保留记录并置 `DISABLED`，用于审计追溯，重新绑定复用同一条记录。

### 4.3 离职与停用

- 组织人员置 `DISABLED`：审批人解析会跳过，但控制台账号是独立的，需要在账号侧停用；
- 控制台账号停用后，即使 IdP 认证通过也会被拒绝（每次回调都校验 `status`）；
- 已经签发的 JWT 靠 `ConsoleAuthFilter` 每次请求查库兜底，停用后立即失效，无需等 token 过期。

## 5. 安全机制

| 风险 | 处置 |
|---|---|
| CSRF / 登录劫持 | `state` 32 字节随机，服务端存储、取出即删 |
| 授权码重放 | PKCE（S256）+ code 只在服务端换 token，前端不接触 client_secret |
| id_token 伪造 | JWKS 按 `kid` 验签，unknown kid 强刷一次（覆盖密钥轮换） |
| 重放旧的 id_token | `nonce` 写入授权请求并与 id_token 比对 |
| audience 混用 | 校验 `aud` 必须包含当前 `client_id` |
| 开放重定向 | 回跳路径只允许站内相对路径，拒绝 `//evil.com`、反斜杠 |
| 密钥泄漏 | `client_secret` 复用 `DataCipher`（AES-256-GCM）落库加密，接口只返回 `******` |
| 越权拿平台权限 | IdP claim 不参与授权；`PLATFORM_ADMIN` 只能由平台侧手工设置 |
| 时序攻击 | state/ticket 比对使用 Map 精确查找 + 一次性消费，不比对用户可控密钥 |
| 时间漂移 | 验签容忍 ±120 秒 |

## 6. 故障排查

| 现象 | 原因与处理 |
|---|---|
| 登录页没有「企业登录」按钮 | 租户未配置或已停用；`easybpm.sso.enabled=false`；租户停用 |
| `STATE_INVALID` | state 超过 5 分钟、已被使用，或**多实例部署**导致回调落到另一台实例 |
| `PROVIDER_UNAVAILABLE` | 配置被停用或租户被停用 |
| `ACCOUNT_NOT_BOUND` | 正常流程：去「企业身份 → 待绑定」绑定账号 |
| `ACCOUNT_DISABLED` | 控制台账号停用，或该身份已被解绑 |
| `IDP_ERROR` + issuer 不一致 | 配置的 issuer 与 IdP discovery 返回的不一致（注意结尾斜杠与 http/https） |
| `IDP_ERROR` + 401 invalid_client | Client Secret 错误，或 IdP 侧是公共客户端 |
| `IDP_ERROR` + redirect_uri mismatch | IdP 登记的地址与 `redirect_uri` 不完全一致 |
| 测试连接通过但登录失败 | 检查 IdP 的 `sub` 是否稳定、id_token 是否为 RS* 签名（ES256 暂不支持） |

## 7. 已知限制

- **state / ticket 存在进程内存**：多副本部署需改 Redis 或改用签名 Cookie 承载 state，否则回调可能落到没有 state 的实例；
- 只支持 RSA 签名的 id_token，使用 ES256 的 IdP 需要扩展 `OidcClient#verifyIdToken`；
- 未实现 SAML 2.0 与 SCIM 增量同步（组织同步仍走 `/open/v1/org/sync`）；
- 未实现单点登出（IdP 侧退出后本地会话不会同步失效，最长残留一个 token TTL）；
- 授权接口未单独限流，建议在网关层对 `/api/auth/sso/*` 做频率限制。
