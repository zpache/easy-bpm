import http from '@/api/request'
import type { OrgDeptView, OrgRoleView, OrgUserView, PageResult } from './types'

export function orgDepts() {
  return http.get<never, OrgDeptView[]>('/api/org/depts')
}

export interface OrgUserParams {
  page?: number
  size?: number
  keyword?: string
  deptCode?: string
  status?: string
}

export function orgUsers(params: OrgUserParams) {
  return http.get<never, PageResult<OrgUserView>>('/api/org/users', { params })
}

export function orgRoles() {
  return http.get<never, OrgRoleView[]>('/api/org/roles')
}
