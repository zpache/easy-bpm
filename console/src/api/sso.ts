import http from '@/api/request'

/** 登录页探测：只返回展示名 */
export interface SsoProviderBrief {
  code: string
  name: string
}

export function listSsoProviders(tenantCode: string) {
  return http.get<never, SsoProviderBrief[]>('/api/auth/sso/providers', { params: { tenantCode } })
}

export interface SsoLoginUser {
  displayName?: string
  role?: string
  username?: string
}

export interface SsoLoginResult {
  token: string
  expiresIn: number
  user: SsoLoginUser
  tenantCode: string
  tenantName: string
  username: string
}

export function exchangeSsoTicket(ticket: string) {
  return http.post<never, SsoLoginResult>('/api/auth/sso/exchange', { ticket })
}

export interface SsoConfig {
  configured: boolean
  id?: string
  code?: string
  name?: string
  protocol?: string
  issuer?: string
  clientId?: string
  clientSecret?: string
  scopes?: string
  redirectUri?: string
  nameClaim?: string
  emailClaim?: string
  status?: string
  autoProvision?: number
  defaultRole?: string
  updatedAt?: string
}

export interface SsoConfigPayload {
  name?: string
  issuer: string
  clientId: string
  clientSecret?: string
  scopes?: string
  redirectUri?: string
  nameClaim?: string
  emailClaim?: string
  status?: string
}

export function getSsoConfig(tenantId: string) {
  return http.get<never, SsoConfig>(`/api/platform/tenants/${tenantId}/sso`)
}

export function saveSsoConfig(tenantId: string, payload: SsoConfigPayload) {
  return http.post<never, SsoConfig>(`/api/platform/tenants/${tenantId}/sso`, payload)
}

export interface SsoTestResult {
  ok: boolean
  message: string
  issuer?: string
  authorizationEndpoint?: string
  tokenEndpoint?: string
  jwksUri?: string
  userinfoEndpoint?: string
}

export function testSsoConnection(tenantId: string, issuer: string) {
  return http.post<never, SsoTestResult>(`/api/platform/tenants/${tenantId}/sso/test`, { issuer })
}

export interface SsoIdentity {
  id: string
  subject: string
  subjectShort: string
  email?: string
  displayName?: string
  status: 'PENDING' | 'ACTIVE' | 'DISABLED'
  username?: string
  consoleDisplayName?: string
  lastAttemptAt?: string
  lastLoginAt?: string
}

export function listSsoIdentities(tenantId: string, status?: string) {
  return http.get<never, SsoIdentity[]>(`/api/platform/tenants/${tenantId}/sso/identities`, {
    params: { status },
  })
}

export interface SsoCandidate {
  id: string
  username: string
  displayName: string
  externalUserId?: string
  authSource?: string
}

export function listSsoCandidates(tenantId: string) {
  return http.get<never, SsoCandidate[]>(`/api/platform/tenants/${tenantId}/sso/candidates`)
}

export function bindSsoIdentity(tenantId: string, identityId: string, username: string, ssoOnly: boolean) {
  return http.post<never, SsoIdentity>(
    `/api/platform/tenants/${tenantId}/sso/identities/${identityId}/bind`,
    { username, ssoOnly },
  )
}

export function unbindSsoIdentity(tenantId: string, identityId: string) {
  return http.post<never, SsoIdentity>(`/api/platform/tenants/${tenantId}/sso/identities/${identityId}/unbind`)
}
