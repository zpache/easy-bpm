import http from '@/api/request'
import type { CcView, InstanceDetail, InstanceView, PageResult, TaskView, WorkbenchCounts } from './types'

export interface PageParams {
  page?: number
  size?: number
  flowCode?: string
  keyword?: string
  status?: string
  unreadOnly?: boolean
}

function toQuery(params: PageParams) {
  const query: Record<string, string | number> = { page: params.page ?? 1, size: params.size ?? 10 }
  if (params.flowCode) query.flowCode = params.flowCode
  if (params.keyword) query.keyword = params.keyword
  if (params.status) query.status = params.status
  return query
}

export function todoTasks(params: PageParams) {
  return http.get<never, PageResult<TaskView>>('/api/workbench/todo', { params: toQuery(params) })
}

export function doneTasks(params: PageParams) {
  return http.get<never, PageResult<TaskView>>('/api/workbench/done', { params: toQuery(params) })
}

export function submittedInstances(params: PageParams) {
  return http.get<never, PageResult<InstanceView>>('/api/workbench/submitted', { params: toQuery(params) })
}

export function workbenchCounts() {
  return http.get<never, WorkbenchCounts>('/api/workbench/counts')
}

export function ccList(params: PageParams) {
  const query: Record<string, string | number | boolean> = { page: params.page ?? 1, size: params.size ?? 10 }
  if (params.keyword) query.keyword = params.keyword
  if (params.unreadOnly) query.unreadOnly = true
  return http.get<never, PageResult<CcView>>('/api/workbench/cc', { params: query })
}

export function markCcRead(id: string) {
  return http.post<never, void>(`/api/workbench/cc/${id}/read`)
}

export function instanceDetail(instanceId: string) {
  return http.get<never, InstanceDetail>(`/api/workbench/instances/${instanceId}`)
}

export function approveTask(taskId: string, comment: string) {
  return http.post<never, unknown>(`/api/workbench/tasks/${taskId}/approve`, { comment })
}

export function rejectTask(taskId: string, comment: string) {
  return http.post<never, unknown>(`/api/workbench/tasks/${taskId}/reject`, { comment })
}

export function dashboardSummary() {
  return http.get<never, Record<string, number>>('/api/dashboard/summary')
}

export interface DashboardTrendPoint {
  day: string
  started: number
  finished: number
}

export interface DashboardFlowStat {
  flowCode: string
  flowName: string
  count: number
}

export interface DashboardStats {
  approvalRate: number
  avgApprovalHours: number
  trend: DashboardTrendPoint[]
  topFlows: DashboardFlowStat[]
}

export function dashboardStats() {
  return http.get<never, DashboardStats>('/api/dashboard/stats')
}

export function allInstances(params: PageParams) {
  return http.get<never, PageResult<InstanceView>>('/api/instances', { params: toQuery(params) })
}

export function forceTransferTask(instanceId: string, taskId: string, targetUserCode: string, comment?: string) {
  return http.post<never, unknown>(`/api/instances/${instanceId}/force-transfer`, { taskId, targetUserCode, comment })
}
