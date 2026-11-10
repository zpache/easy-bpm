import http from '@/api/request'
import type { FlowDefinition, FlowVersion } from './types'

export function listFlows() {
  return http.get<never, FlowDefinition[]>('/api/flows')
}

export function fetchFlow(code: string) {
  return http.get<never, FlowDefinition>(`/api/flows/${code}`)
}

export interface SaveFlowPayload {
  code: string
  name: string
  category?: string
  icon?: string
  description?: string
  formSchema?: unknown
  flowSchema?: unknown
}

export function saveFlow(payload: SaveFlowPayload) {
  return http.post<never, FlowDefinition>('/api/flows', payload)
}

export function publishFlow(code: string, changelog?: string) {
  return http.post<never, FlowDefinition>(`/api/flows/${code}/publish`, { changelog })
}

export function disableFlow(code: string) {
  return http.post<never, void>(`/api/flows/${code}/disable`)
}

export function deleteFlow(code: string) {
  return http.delete<never, void>(`/api/flows/${code}`)
}

export function rollbackFlow(code: string, version: number, changelog?: string) {
  return http.post<never, FlowDefinition>(`/api/flows/${code}/versions/${version}/rollback`, { changelog })
}

export function listVersions(code: string) {
  return http.get<never, FlowVersion[]>(`/api/flows/${code}/versions`)
}
