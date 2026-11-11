import { defineStore } from 'pinia'
import http from '@/api/request'
import type { LoginUser } from '@/api/types'
import type { PlatformTenantSwitch } from '@/api/types'

const TOKEN_KEY = 'easy_bpm_token'
const USER_KEY = 'easy_bpm_user'
const PLATFORM_TOKEN_KEY = 'easy_bpm_platform_token'
const PLATFORM_USER_KEY = 'easy_bpm_platform_user'
const SWITCHED_TENANT_KEY = 'easy_bpm_switched_tenant'

interface LoginResponse {
  token: string
  user: { displayName?: string; role?: string; username?: string }
  username?: string
}

interface SwitchedTenant {
  code: string
  name: string
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: JSON.parse(localStorage.getItem(USER_KEY) || 'null') as LoginUser | null,
    switchedTenant: JSON.parse(localStorage.getItem(SWITCHED_TENANT_KEY) || 'null') as SwitchedTenant | null,
  }),
  getters: {
    loggedIn: (state) => !!state.token,
    isTenantAdmin: (state) => state.user?.role === 'TENANT_ADMIN' || state.user?.role === 'PLATFORM_ADMIN',
    isPlatformAdmin: (state) => state.user?.role === 'PLATFORM_ADMIN',
  },
  actions: {
    async login(tenantCode: string, username: string, password: string) {
      const data = await http.post<never, LoginResponse>('/api/auth/login', {
        tenantCode,
        username,
        password,
      })
      this.applyLogin(data)
    },
    /** 密码登录与 SSO 登录共用：写入本地会话并清空可能残留的平台切换态 */
    applyLogin(data: LoginResponse) {
      this.token = data.token
      this.user = { username: data.username, name: data.user?.displayName, role: data.user?.role }
      this.switchedTenant = null
      localStorage.setItem(TOKEN_KEY, data.token)
      localStorage.setItem(USER_KEY, JSON.stringify(this.user))
      localStorage.removeItem(PLATFORM_TOKEN_KEY)
      localStorage.removeItem(PLATFORM_USER_KEY)
      localStorage.removeItem(SWITCHED_TENANT_KEY)
    },
    /** 平台管理员切换进入某租户：保存平台会话，启用目标租户 token */
    enterTenant(payload: PlatformTenantSwitch) {
      localStorage.setItem(PLATFORM_TOKEN_KEY, this.token)
      if (this.user) {
        localStorage.setItem(PLATFORM_USER_KEY, JSON.stringify(this.user))
      }
      this.token = payload.token
      this.user = { username: payload.displayName || 'platadmin', name: payload.displayName, role: 'PLATFORM_ADMIN' }
      this.switchedTenant = { code: payload.tenantCode, name: payload.tenantName }
      localStorage.setItem(SWITCHED_TENANT_KEY, JSON.stringify(this.switchedTenant))
    },
    /** 返回平台管理视角：恢复平台会话 */
    exitTenant() {
      const token = localStorage.getItem(PLATFORM_TOKEN_KEY)
      const user = localStorage.getItem(PLATFORM_USER_KEY)
      if (token) this.token = token
      if (user) this.user = JSON.parse(user)
      this.switchedTenant = null
      localStorage.removeItem(PLATFORM_TOKEN_KEY)
      localStorage.removeItem(PLATFORM_USER_KEY)
      localStorage.removeItem(SWITCHED_TENANT_KEY)
    },
    clear() {
      this.token = ''
      this.user = null
      this.switchedTenant = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
      localStorage.removeItem(PLATFORM_TOKEN_KEY)
      localStorage.removeItem(PLATFORM_USER_KEY)
      localStorage.removeItem(SWITCHED_TENANT_KEY)
    },
    async logout() {
      this.clear()
    },
  },
})
