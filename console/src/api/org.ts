import http from '@/api/request'
import type { OrgDeptView, OrgRoleView, OrgUserView, PageResult } from './types'

export function orgDepts() {
  return http.get<never, OrgDeptView[]>('/org/depts')
}

export interface OrgUserParams {
  page?: number
  size?: number
  keyword?: string
  deptCode?: string
  status?: string
}

export function orgUsers(params: OrgUserParams) {
  return http.get<never, PageResult<OrgUserView>>('/org/users', { params })
}

export function orgRoles() {
  return http.get<never, OrgRoleView[]>('/org/roles')
}

export interface SaveDeptPayload {
  deptCode: string
  name: string
  parentCode?: string
  leaderId?: string
  sortNo?: number
}

export function saveDept(payload: SaveDeptPayload) {
  return http.post<never, void>('/org/depts', payload)
}

export function deleteDept(deptCode: string) {
  return http.delete<never, void>(`/org/depts/${deptCode}`)
}

export interface SaveUserPayload {
  userCode: string
  name: string
  deptCode?: string
  leaderCode?: string
  title?: string
  mobile?: string
  email?: string
  status?: string
  /** 编辑时全量替换该人员的角色；不传表示不改动 */
  roleCodes?: string[]
}

export function saveUser(payload: SaveUserPayload) {
  return http.post<never, void>('/org/users', payload)
}

export function deleteUser(userCode: string) {
  return http.delete<never, void>(`/org/users/${userCode}`)
}

export function orgRoleUsers(roleCode: string) {
  return http.get<never, string[]>(`/org/roles/${roleCode}/users`)
}

export function saveRole(payload: { roleCode: string; roleName: string }) {
  return http.post<never, void>('/org/roles', payload)
}

export function deleteRole(roleCode: string) {
  return http.delete<never, void>(`/org/roles/${roleCode}`)
}

export interface RoleAssignResult {
  added: number
  removed: number
}

export function assignRole(roleCode: string, userCodes: string[]) {
  return http.post<never, RoleAssignResult>(`/org/roles/${roleCode}/assign`, { userCodes })
}
