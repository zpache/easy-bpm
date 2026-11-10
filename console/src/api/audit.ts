import http from '@/api/request'
import type { AuditView, PageResult } from './types'

export interface AuditLogParams {
  page?: number
  size?: number
  action?: string
  keyword?: string
}

export function auditLogs(params: AuditLogParams) {
  return http.get<never, PageResult<AuditView>>('/api/audit/logs', { params })
}
