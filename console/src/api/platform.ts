import http from '@/api/request'
import type { PageResult, PlatformTenantView, PlatformTenantSwitch } from './types'

export interface TenantListParams {
  page?: number
  size?: number
  keyword?: string
}

export function listTenants(params: TenantListParams = {}) {
  return http.get<never, PageResult<PlatformTenantView>>('/api/platform/tenants', { params })
}

export interface CreateTenantPayload {
  code: string
  name: string
  remark?: string
  adminUsername?: string
  adminPassword?: string
  adminDisplayName?: string
}

export function createTenant(payload: CreateTenantPayload) {
  return http.post<never, PlatformTenantView>('/api/platform/tenants', payload)
}

export interface UpdateTenantPayload {
  name?: string
  remark?: string
}

export function updateTenant(id: string, payload: UpdateTenantPayload) {
  return http.post<never, PlatformTenantView>(`/api/platform/tenants/${id}`, payload)
}

export function disableTenant(id: string) {
  return http.post<never, PlatformTenantView>(`/api/platform/tenants/${id}/disable`)
}

export function enableTenant(id: string) {
  return http.post<never, PlatformTenantView>(`/api/platform/tenants/${id}/enable`)
}

export function switchTenant(id: string) {
  return http.post<never, PlatformTenantSwitch>(`/api/platform/tenants/${id}/switch`)
}
