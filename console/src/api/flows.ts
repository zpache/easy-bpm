import http from '@/api/request'
import type { FlowDefinition, FlowVersion, PageResult } from './types'

export function listFlows() {
  return http.get<never, FlowDefinition[]>('/flows')
}

export function pageFlows(page: number, size: number) {
  return http.get<never, PageResult<FlowDefinition>>('/flows', { params: { page, size } })
}

export function fetchFlow(code: string) {
  return http.get<never, FlowDefinition>(`/flows/${code}`)
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
  return http.post<never, FlowDefinition>('/flows', payload)
}

export function publishFlow(code: string, changelog?: string) {
  return http.post<never, FlowDefinition>(`/flows/${code}/publish`, { changelog })
}

export function disableFlow(code: string) {
  return http.post<never, void>(`/flows/${code}/disable`)
}

export function enableFlow(code: string) {
  return http.post<never, FlowDefinition>(`/flows/${code}/enable`)
}

export function deleteFlow(code: string) {
  return http.delete<never, void>(`/flows/${code}`)
}

export function rollbackFlow(code: string, version: number, changelog?: string) {
  return http.post<never, FlowDefinition>(`/flows/${code}/versions/${version}/rollback`, { changelog })
}

export function listVersions(code: string) {
  return http.get<never, FlowVersion[]>(`/flows/${code}/versions`)
}

export interface FlowFormField {
  key: string
  type: string
  samples: unknown[]
}

/** 该流程历史实例 formData 的顶层字段聚合（近 100 条），用于条件分支配置时的字段键补全 */
export function flowFormFields(code: string) {
  return http.get<never, FlowFormField[]>(`/flows/${code}/form-fields`)
}

export interface ConditionTestResult {
  matched: boolean[]
  firstMatchIndex: number
}

/** 条件试跑：用样例 formData 逐一求值，返回各表达式命中结果与第一个命中的下标 */
export function conditionTest(code: string, expressions: string[], formData: Record<string, unknown>) {
  return http.post<never, ConditionTestResult>(`/flows/${code}/condition-test`, { expressions, formData })
}
