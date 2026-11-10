/** 后端统一响应 */
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  traceId?: string
}

export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  size: number
}

export interface LoginUser {
  username: string
  name: string
  role: string
  userCode?: string
}

export interface FlowDefinition {
  code: string
  name: string
  category?: string
  icon?: string
  description?: string
  status: 'DRAFT' | 'PUBLISHED' | 'DISABLED'
  version: number
  processDefinitionId?: string
  formSchema?: Record<string, unknown> | null
  flowSchema?: Record<string, unknown> | null
  updatedAt?: string
  publishedAt?: string
}

export interface FlowVersion {
  version: number
  processDefinitionId?: string
  publishedBy?: string
  publishedAt?: string
  changelog?: string
}

export interface TaskView {
  taskId: string
  processInstanceId: string
  nodeId?: string
  nodeName?: string
  assignee?: string
  assigneeName?: string
  instanceTitle?: string
  flowCode?: string
  flowName?: string
  initiatorName?: string
  status: 'PENDING' | 'FINISHED'
  action?: string
  createTime?: string
  endTime?: string
}

export interface InstanceView {
  instanceId: string
  flowCode: string
  flowName?: string
  flowVersion?: number
  businessKey?: string
  title: string
  initiatorId?: string
  initiatorName?: string
  formData?: Record<string, unknown>
  status: 'RUNNING' | 'APPROVED' | 'REJECTED' | 'CANCELED'
  currentNodeName?: string
  startedAt?: string
  endedAt?: string
}

export interface TimelineItem {
  action: string
  nodeName?: string
  operatorId?: string
  operatorName?: string
  comment?: string
  createdAt?: string
}

export interface InstanceDetail {
  instance: InstanceView
  timeline: TimelineItem[]
  pendingTasks: TaskView[]
  formSchema?: Record<string, unknown> | null
}

export interface WorkbenchCounts {
  todo: number
  done: number
  submitted: number
}

export interface OrgDeptView {
  deptCode: string
  name: string
  parentCode?: string
  leaderId?: string
  sortNo?: number
  updatedAt?: string
}

export interface OrgUserView {
  userCode: string
  name: string
  deptCode?: string
  deptName?: string
  leaderCode?: string
  title?: string
  mobile?: string
  email?: string
  status: string
  updatedAt?: string
}

export interface OrgRoleView {
  roleCode: string
  userCount: number
}

export interface TenantInfo {
  code: string
  name: string
  status: string
  remark?: string
  createdAt?: string
}

export interface TenantCredentialView {
  id: string
  name: string
  appKey: string
  appSecretMasked: string
  status: string
  expireAt?: string
  lastUsedAt?: string
  createdAt?: string
}

export interface CredentialSecretView {
  id: string
  name: string
  appKey: string
  appSecret: string
  createdAt?: string
}
