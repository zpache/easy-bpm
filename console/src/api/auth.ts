import http from '@/api/request'
import type { LoginUser } from './types'

export function login(tenantCode: string, username: string, password: string) {
  return http.post<never, { token: string; user: LoginUser }>('/api/auth/login', {
    tenantCode,
    username,
    password,
  })
}

export function fetchMe() {
  return http.get<never, LoginUser>('/api/auth/me')
}
