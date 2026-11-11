import http from '@/api/request'
import type { CredentialSecretView, TenantCredentialView, TenantInfo } from './types'

export function tenantInfo() {
  return http.get<never, TenantInfo>('/api/tenant/info')
}

export function listCredentials() {
  return http.get<never, TenantCredentialView[]>('/api/tenant/credentials')
}

export interface CreateCredentialPayload {
  name: string
  expireAt?: string
}

export function createCredential(payload: CreateCredentialPayload) {
  return http.post<never, CredentialSecretView>('/api/tenant/credentials', payload)
}

export function rotateCredential(id: string) {
  return http.post<never, CredentialSecretView>(`/api/tenant/credentials/${id}/rotate`)
}

export function disableCredential(id: string) {
  return http.post<never, void>(`/api/tenant/credentials/${id}/disable`)
}

export function enableCredential(id: string) {
  return http.post<never, void>(`/api/tenant/credentials/${id}/enable`)
}
