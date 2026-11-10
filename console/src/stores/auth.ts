import { defineStore } from 'pinia'
import http from '@/api/request'
import type { LoginUser } from '@/api/types'

const TOKEN_KEY = 'easy_bpm_token'
const USER_KEY = 'easy_bpm_user'

interface LoginResponse {
  token: string
  user: LoginUser
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: JSON.parse(localStorage.getItem(USER_KEY) || 'null') as LoginUser | null,
  }),
  getters: {
    loggedIn: (state) => !!state.token,
    isTenantAdmin: (state) => state.user?.role === 'TENANT_ADMIN',
  },
  actions: {
    async login(tenantCode: string, username: string, password: string) {
      const data = await http.post<never, LoginResponse>('/api/auth/login', {
        tenantCode,
        username,
        password,
      })
      this.token = data.token
      this.user = data.user
      localStorage.setItem(TOKEN_KEY, data.token)
      localStorage.setItem(USER_KEY, JSON.stringify(data.user))
    },
    clear() {
      this.token = ''
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    },
    async logout() {
      this.clear()
    },
  },
})
