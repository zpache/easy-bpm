/**
 * easy-bpm 控制台 SSO 本地测试 IdP（基于 oidc-provider）。
 *
 * 用途：没有真实企业 IdP / 未装 Docker 时，本地模拟一个标准 OIDC 提供方，
 * 完整跑通 授权码 + PKCE + id_token(RS256) + userinfo 链路。
 *
 * 启动：
 *   cd tools/mock-idp && npm install && npm start
 *   （端口默认 4444，可用 PORT 环境变量覆盖）
 *
 * 在 easy-bpm「平台管理 → 租户 SSO」中配置：
 *   Issuer        http://localhost:4444
 *   Client ID     easy-bpm
 *   Client Secret mock-secret-2026
 *   （回调地址用默认 http://localhost:8091/api/auth/sso/callback 即可，
 *     本脚本已按该地址登记 redirect_uris）
 *
 * 测试账号：devInteractions 内置登录页里随便输入一个邮箱即可，
 *   sub = 输入的邮箱，name = "Mock 用户"，email = 输入的邮箱。
 */
import Provider from 'oidc-provider';

const PORT = Number(process.env.PORT || 4444);
const ISSUER = process.env.ISSUER || `http://localhost:${PORT}`;

const provider = new Provider(ISSUER, {
  clients: [
    {
      client_id: 'easy-bpm',
      client_secret: 'mock-secret-2026',
      // easy-bpm 服务端用表单体（client_secret_post）提交凭据
      token_endpoint_auth_method: 'client_secret_post',
      grant_types: ['authorization_code'],
      response_types: ['code'],
      redirect_uris: [
        'http://localhost:8091/api/auth/sso/callback',
        'http://localhost:8090/api/auth/sso/callback',
      ],
      post_logout_redirect_uris: [],
    },
  ],
  claims: {
    profile: ['name', 'nickname', 'preferred_username'],
    email: ['email', 'email_verified'],
  },
  // 内置开发登录页：任意输入一个「邮箱」即视为认证成功
  features: { devInteractions: { enabled: true } },
  findAccount: async (_ctx, id) => ({
    accountId: id,
    claims: async () => ({
      sub: id,
      name: `Mock 用户 ${id}`,
      email: id.includes('@') ? id : `${id}@mock.local`,
      email_verified: true,
    }),
  }),
  // 固定 cookie 密钥，避免重启后 session 失效干扰调试
  cookies: { keys: ['easy-bpm-mock-idp-debug-key'] },
});

provider.listen(PORT, () => {
  console.log(`mock IdP 已启动`);
  console.log(`  issuer        : ${ISSUER}`);
  console.log(`  discovery     : ${ISSUER}/.well-known/openid-configuration`);
  console.log(`  client_id     : easy-bpm`);
  console.log(`  client_secret : mock-secret-2026`);
  console.log(`  redirect_uris : http://localhost:8091/api/auth/sso/callback`);
  console.log(`登录页任意输入一个邮箱即可完成认证（sub=该邮箱）。`);
});
