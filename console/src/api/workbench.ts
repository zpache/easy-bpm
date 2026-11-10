import http from '@/api/request'
import type { InstanceDetail, InstanceView, PageResult, TaskView, WorkbenchCounts } from './types'

export interface PageParams {
  page?: number
  size?: number
  flowCode?: string
  keyword?: string
  status?: string
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
