<template>
  <el-card>
    <div class="toolbar">
      <el-button @click="router.push({ name: 'flows' })">返回</el-button>
      <span class="flow-title">{{ flow?.name }}（{{ code }}）· 审批流程设计</span>
      <span style="flex: 1" />
      <el-button v-if="auth.isTenantAdmin" :disabled="flow?.status === 'DISABLED'" @click="addApprovalNode">+ 审批节点</el-button>
      <el-button v-if="auth.isTenantAdmin" :disabled="flow?.status === 'DISABLED'" @click="addCcNode">+ 抄送节点</el-button>
      <el-button v-if="auth.isTenantAdmin" :disabled="flow?.status === 'DISABLED'" @click="addGatewayNode">+ 条件分支</el-button>
      <el-button v-if="auth.isTenantAdmin" :disabled="flow?.status === 'DISABLED'" @click="onSave">保存草稿</el-button>
      <el-button v-if="auth.isTenantAdmin" type="success" :disabled="flow?.status === 'DISABLED'" :loading="publishing" @click="onPublish">发布</el-button>
    </div>

    <el-alert v-if="flow?.status === 'DISABLED'" type="warning" :closable="false" style="margin-bottom: 12px">
      流程已停用，仅可查看。
    </el-alert>

    <div class="designer">
      <template v-for="(node, index) in nodes" :key="node.id + index">
        <!-- 条件网关：分支面板 -->
        <div v-if="node.type === 'CONDITION'" class="node gateway"
             :class="{ selected: selectedIndex === index, editable: canEditNode(node) }" @click="openGatewayEdit(index)">
          <div class="node-type">条件分支 · 按顺序命中第一个满足条件的分支</div>
          <div class="node-name">{{ node.name }}</div>
          <div class="gateway-branches">
            <div v-for="(branch, bi) in node.branches ?? []" :key="bi" class="branch-panel"
                 :class="{ 'branch-default': !branch.condition?.trim() }">
              <div class="branch-head">
                <span class="branch-name">{{ branch.name }}</span>
                <el-tag v-if="!branch.condition?.trim()" size="small" type="info" effect="plain">默认</el-tag>
                <el-tag v-else size="small" type="warning" effect="plain">{{ branch.condition }}</el-tag>
              </div>
              <template v-for="(child, ci) in branch.nodes ?? []" :key="child.id + ci">
                <div class="node branch-node" :class="[nodeClass(child), { editable: canEditNode(child) }]"
                     @click.stop="openBranchNodeEdit(index, bi, ci)">
                  <div class="node-type">{{ nodeLabel(child.type) }}</div>
                  <div class="node-name small">{{ child.name }}</div>
                  <div v-if="child.type === 'APPROVAL' && child.props" class="node-props">
                    {{ approverText(child.props) }} · {{ child.props.mode === 'ALL' ? '会签' : '或签' }}
                  </div>
                  <div v-if="child.type === 'CC' && child.props" class="node-props">抄送：{{ ccText(child.props) }}</div>
                  <el-tag v-if="child.condition" size="small" type="warning" style="margin-top: 4px">条件：{{ child.condition }}</el-tag>
                </div>
                <div class="branch-arrow">↓</div>
              </template>
              <div v-if="canEditNode(node)" class="branch-add">
                <el-button size="small" text type="primary" @click.stop="addBranchNode(index, bi, 'APPROVAL')">＋审批</el-button>
                <el-button size="small" text type="success" @click.stop="addBranchNode(index, bi, 'CC')">＋抄送</el-button>
              </div>
              <div v-if="!(branch.nodes ?? []).length" class="branch-empty">暂无节点</div>
            </div>
          </div>
        </div>

        <!-- 普通节点 -->
        <div v-else class="node" :class="[nodeClass(node), { selected: selectedIndex === index, editable: canEditNode(node) }]" @click="openNodeEdit(index)">
          <span v-if="node.type === 'START' || node.type === 'END'" class="fixed-tag">流程固定</span>
          <div class="node-type">{{ nodeLabel(node.type) }}</div>
          <div class="node-name">{{ node.name }}</div>
          <div v-if="node.type === 'APPROVAL' && node.props" class="node-props">
            {{ approverText(node.props) }} · {{ node.props.mode === 'ALL' ? '会签' : '或签' }}
            <template v-if="node.props.rejectStrategy === 'TO_START'"> · 驳回退回发起人</template>
            <template v-if="node.props.emptyStrategy === 'AUTO_PASS'"> · 空时自动通过</template>
            <template v-else-if="node.props.emptyStrategy === 'AUTO_REJECT'"> · 空时自动驳回</template>
          </div>
          <div v-if="node.type === 'CC' && node.props" class="node-props">
            抄送给：{{ ccText(node.props) }}
          </div>
          <el-tag v-if="node.condition" size="small" type="warning" style="margin-top: 6px">条件：{{ node.condition }}</el-tag>
        </div>
        <div class="connector">
          <template v-if="canInsertAfter(index)">
            <span class="seg"></span>
            <el-button circle size="small" class="insert-btn" @click.stop="insertAfter(index)">＋</el-button>
            <span class="seg short"></span>
            <span class="head"></span>
          </template>
          <template v-else>
            <span class="seg"></span>
            <span class="head"></span>
          </template>
        </div>
      </template>
    </div>

    <!-- 节点编辑抽屉（主链 + 分支内节点共用） -->
    <el-drawer v-model="drawerVisible" :title="isNewNode ? '新增节点' : `编辑${nodeLabel(editing?.type ?? 'APPROVAL')}节点`" size="440px">
      <el-form v-if="editing" :model="editing" label-width="100px">
        <el-alert type="info" :closable="false" style="margin-bottom: 16px">
          带 <span style="color: #f56c6c">*</span> 为必填项
        </el-alert>
        <el-form-item label="节点名称" required>
          <el-input v-model="editing.name" />
        </el-form-item>
        <el-form-item v-if="editing.type === 'CC'" label="抄送人" required>
          <el-input v-model="ccUsersInput" placeholder="逗号分隔 userCode" />
        </el-form-item>
        <el-form-item v-if="editing.type !== 'CC'" label="条件表达式">
          <el-input v-model="editing.condition" placeholder="如 days > 3，留空表示无条件" />
        </el-form-item>
        <template v-if="editing.type === 'APPROVAL'">
          <el-form-item label="审批人类型" required>
            <el-select v-model="editing.props!.approverType">
              <el-option label="发起人直属主管" value="INITIATOR_LEADER" />
              <el-option label="部门主管" value="DEPT_LEADER" />
              <el-option label="指定人员" value="SPECIFIED" />
              <el-option label="指定角色" value="ROLE" />
              <el-option label="表单字段" value="FORM_FIELD" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="editing.props!.approverType === 'INITIATOR_LEADER'" label="主管级别">
            <el-input-number v-model="editing.props!.leaderLevel" :min="1" :max="5" />
          </el-form-item>
          <el-form-item v-if="editing.props!.approverType === 'SPECIFIED'" label="指定人员" required>
            <el-select v-model="specifiedUsersInput" multiple filterable allow-create
                       default-first-option placeholder="从组织架构选择，或输入 userCode" style="width: 100%">
              <el-option v-for="u in userOptions" :key="u.userCode"
                         :label="`${u.name}（${u.userCode}）`" :value="u.userCode" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="editing.props!.approverType === 'ROLE'" label="指定角色" required>
            <el-select v-model="roleCodesInput" multiple filterable allow-create
                       default-first-option placeholder="从组织架构选择角色，或输入角色编码" style="width: 100%">
              <el-option v-for="r in roleOptions" :key="r.roleCode"
                         :label="`${r.roleCode}（${r.userCount} 人）`" :value="r.roleCode" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="editing.props!.approverType === 'FORM_FIELD'" label="表单字段" required>
            <el-input v-model="editing.props!.formFieldKey" placeholder="值为 userCode 的字段名" />
          </el-form-item>
          <el-form-item label="审批方式" required>
            <el-select v-model="editing.props!.mode">
              <el-option label="或签（一人通过即过）" value="ANY" />
              <el-option label="会签（全部通过才过）" value="ALL" />
            </el-select>
          </el-form-item>
          <el-form-item label="驳回策略" required>
            <el-select v-model="editing.props!.rejectStrategy">
              <el-option label="驳回到结束" value="TO_END" />
              <el-option label="退回发起人重提" value="TO_START" />
            </el-select>
          </el-form-item>
          <el-form-item label="审批人为空时">
            <el-select v-model="editing.props!.emptyStrategy">
              <el-option label="拦截发起（默认）" value="FAIL" />
              <el-option label="自动通过" value="AUTO_PASS" />
              <el-option label="自动驳回" value="AUTO_REJECT" />
            </el-select>
            <div class="field-hint">审批人全部被停用或匹配不到时的兜底动作</div>
          </el-form-item>
        </template>
        <el-button type="danger" plain style="width: 100%" @click="removeNode">删除该节点</el-button>
      </el-form>
    </el-drawer>

    <!-- 条件网关编辑抽屉 -->
    <el-drawer v-model="gatewayVisible" title="编辑条件分支" size="620px">
      <el-form v-if="gatewayDraft" label-width="90px">
        <el-form-item label="网关名称" required>
          <el-input v-model="gatewayDraft.name" />
        </el-form-item>
        <el-alert type="info" :closable="false" style="margin-bottom: 12px">
          分支按顺序求值，命中第一个满足条件的分支后走该分支的子节点链；
          <b>条件留空的是默认分支</b>（必须放在最后，且只能有一个），所有条件都不满足时走它。
        </el-alert>

        <div v-for="(branch, bi) in gatewayDraft.branches" :key="bi" class="gw-branch">
          <div class="gw-branch-head">
            <span class="gw-branch-title">分支 {{ bi + 1 }}</span>
            <el-tag v-if="!branch.condition?.trim()" size="small" type="info">默认分支</el-tag>
            <el-button v-if="bi < (gatewayDraft?.branches?.length ?? 0) - 1" link type="danger" size="small"
                       style="margin-left: auto" @click="removeGatewayBranch(bi)">删除分支</el-button>
          </div>
          <el-form-item label="分支名称" required>
            <el-input v-model="branch.name" placeholder="如：大额申请" />
          </el-form-item>
          <el-form-item v-if="bi < (gatewayDraft?.branches?.length ?? 0) - 1" label="命中条件" required>
            <div class="cond-editor">
              <el-radio-group v-model="branchModes[bi]" size="small">
                <el-radio-button value="rule">规则模式</el-radio-button>
                <el-radio-button value="expr">表达式</el-radio-button>
              </el-radio-group>
              <template v-if="branchModes[bi] === 'rule'">
                <div v-for="(rule, ri) in branchRules[bi] ?? []" :key="ri" class="rule-row">
                  <el-select v-model="rule.field" filterable allow-create default-first-option
                             placeholder="字段" style="width: 38%">
                    <el-option v-for="f in fieldOptions" :key="f.key" :value="f.key"
                               :label="`${f.key}（${f.type}）`" />
                  </el-select>
                  <el-select v-model="rule.op" style="width: 22%">
                    <el-option v-for="op in OPERATORS" :key="op" :value="op" :label="op" />
                  </el-select>
                  <el-input v-model="rule.value" placeholder="值" style="width: 30%" />
                  <el-button link type="danger" @click="removeRule(bi, ri)">删</el-button>
                </div>
                <el-button size="small" text type="primary" @click="addRule(bi)">＋ 且条件</el-button>
                <div v-if="branch.condition" class="field-hint">生成表达式：{{ branch.condition }}</div>
              </template>
              <el-input v-else v-model="branch.condition" placeholder="SpEL，如 amount > 10000 && category == 'travel'" />
            </div>
          </el-form-item>
          <el-form-item v-else label="命中条件">
            <div class="field-hint">默认分支不配置条件，其余分支全部未命中时走此分支。</div>
          </el-form-item>
          <el-form-item label="包含节点">
            <div class="field-hint">
              {{ (branch.nodes ?? []).map((n) => n.name).join(' → ') || '分支内暂无节点（至少需要 1 个）' }}
              ，在左侧画布的分支面板中维护。
            </div>
          </el-form-item>
        </div>

        <el-button style="width: 100%" @click="addGatewayBranch">＋ 添加分支</el-button>
        <div style="display: flex; gap: 8px; margin-top: 16px">
          <el-button style="flex: 1" @click="openTestDialog">试跑选路</el-button>
          <el-button style="flex: 1" type="primary" @click="saveGateway">完成</el-button>
        </div>
      </el-form>
    </el-drawer>

    <!-- 条件试跑弹窗 -->
    <el-dialog v-model="testVisible" title="条件试跑" width="520px">
      <el-alert type="info" :closable="false" style="margin-bottom: 12px"
        title="粘贴一份样例业务数据（formData JSON），查看各分支的命中情况。" />
      <el-input v-model="testFormData" type="textarea" :rows="6" placeholder='{"amount": 15000, "category": "travel"}' />
      <div v-if="testResult" class="test-result">
        <div v-for="(m, i) in testResult.matched" :key="i" class="test-row">
          <span>{{ gatewayDraft?.branches?.[i]?.name ?? `分支 ${i + 1}` }}</span>
          <el-tag size="small" :type="m ? 'success' : 'info'">{{ m ? '命中' : '未命中' }}</el-tag>
        </div>
        <div class="field-hint" style="margin-top: 8px">
          {{ testResult.firstMatchIndex >= 0
            ? `实际走向：${gatewayDraft?.branches?.[testResult.firstMatchIndex]?.name ?? '分支 ' + (testResult.firstMatchIndex + 1)}（FIRST-MATCH）`
            : '所有条件均未命中，将走默认分支' }}
        </div>
      </div>
      <template #footer>
        <el-button @click="testVisible = false">关闭</el-button>
        <el-button type="primary" :loading="testing" @click="runTest">试跑</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { conditionTest, fetchFlow, flowFormFields, publishFlow, saveFlow } from '@/api/flows'
import type { FlowFormField } from '@/api/flows'
import { orgRoles, orgUsers } from '@/api/org'
import { useAuthStore } from '@/stores/auth'
import type { OrgRoleView, OrgUserView } from '@/api/types'

interface NodeProps {
  approverType?: string
  leaderLevel?: number
  specifiedUsers?: string | string[]
  roleCodes?: string | string[]
  formFieldKey?: string
  mode?: string
  rejectStrategy?: string
  emptyStrategy?: string
  ccUsers?: string | string[]
}

interface FlowBranch {
  name: string
  condition?: string
  nodes: FlowNode[]
}

interface FlowNode {
  id: string
  type: 'START' | 'APPROVAL' | 'CC' | 'END' | 'CONDITION'
  name: string
  condition?: string
  props?: NodeProps
  branches?: FlowBranch[]
}

const OPERATORS = ['>', '>=', '<', '<=', '==', '!='] as const
interface RuleRow { field: string; op: string; value: string }

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const code = computed(() => route.params.code as string)
const flow = ref<Awaited<ReturnType<typeof fetchFlow>> | null>(null)
const nodes = ref<FlowNode[]>([])
const selectedIndex = ref(-1)
const drawerVisible = ref(false)
const editing = ref<(FlowNode & { props: NodeProps }) | null>(null)
const isNewNode = ref(false)

// 节点编辑上下文：主链或网关分支内
type EditCtx = { scope: 'main'; index: number } | { scope: 'branch'; gw: number; branch: number; node: number }
const editCtx = ref<EditCtx>({ scope: 'main', index: -1 })

function canEditNode(node: FlowNode): boolean {
  return auth.isTenantAdmin && flow.value?.status !== 'DISABLED'
    && (node.type === 'APPROVAL' || node.type === 'CC' || node.type === 'CONDITION')
}

function openNodeEdit(index: number) {
  selectedIndex.value = index
  const node = nodes.value[index]
  if (!canEditNode(node)) return
  if (node.type === 'CONDITION') {
    openGatewayEdit(index)
    return
  }
  editCtx.value = { scope: 'main', index }
  // 属性缺省值补齐，保证抽屉表单可完整编辑
  if (node.type === 'APPROVAL') {
    node.props = { approverType: 'INITIATOR_LEADER', leaderLevel: 1, mode: 'ANY', rejectStrategy: 'TO_END', emptyStrategy: 'FAIL', ...node.props }
  } else if (node.type === 'CC') {
    node.props = { ccUsers: '', ...node.props }
  }
  editing.value = node as FlowNode & { props: NodeProps }
  isNewNode.value = false
  drawerVisible.value = true
}

function openBranchNodeEdit(gw: number, branch: number, node: number) {
  const gateway = nodes.value[gw]
  const target = gateway.branches?.[branch]?.nodes?.[node]
  if (!target || !canEditNode(target)) return
  selectedIndex.value = gw
  editCtx.value = { scope: 'branch', gw, branch, node }
  if (target.type === 'APPROVAL') {
    target.props = { approverType: 'INITIATOR_LEADER', leaderLevel: 1, mode: 'ANY', rejectStrategy: 'TO_END', emptyStrategy: 'FAIL', ...target.props }
  } else if (target.type === 'CC') {
    target.props = { ccUsers: '', ...target.props }
  }
  editing.value = target as FlowNode & { props: NodeProps }
  isNewNode.value = false
  drawerVisible.value = true
}

function nodeClass(node: FlowNode) {
  return {
    start: node.type === 'START', approval: node.type === 'APPROVAL',
    cc: node.type === 'CC', end: node.type === 'END', gateway: node.type === 'CONDITION',
  }
}

function nodeLabel(type: FlowNode['type']) {
  return { START: '发起', APPROVAL: '审批', CC: '抄送', END: '结束', CONDITION: '条件分支' }[type]
}

function approverText(props: NodeProps): string {
  switch (props.approverType) {
    case 'INITIATOR_LEADER': return `直属主管 L${props.leaderLevel ?? 1}`
    case 'DEPT_LEADER': return '部门主管'
    case 'SPECIFIED': return typeof props.specifiedUsers === 'string' ? props.specifiedUsers : (props.specifiedUsers ?? []).join(',')
    case 'ROLE': return '角色 ' + (typeof props.roleCodes === 'string' ? props.roleCodes : (props.roleCodes ?? []).join(','))
    case 'FORM_FIELD': return `字段 ${props.formFieldKey}`
    default: return '审批人'
  }
}

function ccText(props: NodeProps): string {
  const users = typeof props.ccUsers === 'string' ? props.ccUsers : (props.ccUsers ?? []).join(',')
  return users || '未配置'
}

const ccUsersInput = computed({
  get: () => {
    const value = editing.value?.props?.ccUsers
    return (typeof value === 'string' ? value : (value ?? []).join(',')) || ''
  },
  set: (input: string) => {
    if (editing.value?.props) editing.value.props.ccUsers = input
  },
})

// 组织架构人员/角色选项，供审批人下拉选择
const userOptions = ref<OrgUserView[]>([])
const roleOptions = ref<OrgRoleView[]>([])

// 该流程历史 formData 字段聚合，供条件规则模式下拉选择
const fieldOptions = ref<FlowFormField[]>([])

const specifiedUsersInput = computed({
  get: () => {
    const value = editing.value?.props?.specifiedUsers
    if (Array.isArray(value)) return value
    return value ? value.split(/[,，\s]+/).filter(Boolean) : []
  },
  set: (list: string[]) => {
    if (editing.value?.props) editing.value.props.specifiedUsers = list
  },
})

const roleCodesInput = computed({
  get: () => {
    const value = editing.value?.props?.roleCodes
    if (Array.isArray(value)) return value
    return value ? value.split(/[,，\s]+/).filter(Boolean) : []
  },
  set: (list: string[]) => {
    if (editing.value?.props) editing.value.props.roleCodes = list
  },
})

function canInsertAfter(index: number): boolean {
  if (!auth.isTenantAdmin) return false
  const node = nodes.value[index]
  const next = nodes.value[index + 1]
  return node.type !== 'START' && node.type !== 'END' && next && next.type === 'END'
}

function insertAfter(index: number, type: 'APPROVAL' | 'CC' = 'APPROVAL') {
  const node: FlowNode & { props: NodeProps } = type === 'CC'
    ? { id: 'c' + Date.now().toString(36), type: 'CC', name: '抄送', props: { ccUsers: '' } }
    : {
        id: 'n' + Date.now().toString(36),
        type: 'APPROVAL',
        name: '新审批节点',
        props: { approverType: 'INITIATOR_LEADER', leaderLevel: 1, mode: 'ANY', rejectStrategy: 'TO_END' },
      }
  nodes.value.splice(index + 1, 0, node)
  selectedIndex.value = index + 1
  editing.value = node
  isNewNode.value = true
  drawerVisible.value = true
}

function addApprovalNode() {
  const endIndex = nodes.value.findIndex((n) => n.type === 'END')
  if (endIndex > 0) insertAfter(endIndex - 1, 'APPROVAL')
}

function addCcNode() {
  const endIndex = nodes.value.findIndex((n) => n.type === 'END')
  if (endIndex > 0) insertAfter(endIndex - 1, 'CC')
}

function newApproval(name = '新审批节点'): FlowNode {
  return {
    id: 'n' + Date.now().toString(36) + Math.random().toString(36).slice(2, 5),
    type: 'APPROVAL', name,
    props: { approverType: 'INITIATOR_LEADER', leaderLevel: 1, mode: 'ANY', rejectStrategy: 'TO_END' },
  }
}

function addGatewayNode() {
  const endIndex = nodes.value.findIndex((n) => n.type === 'END')
  if (endIndex <= 0) return
  const gateway: FlowNode = {
    id: 'g' + Date.now().toString(36),
    type: 'CONDITION',
    name: '条件分支',
    branches: [
      { name: '分支一', condition: '', nodes: [newApproval('分支一审批')] },
      { name: '默认分支', condition: '', nodes: [newApproval('默认审批')] },
    ],
  }
  nodes.value.splice(endIndex, 0, gateway)
  openGatewayEdit(endIndex)
}

function addBranchNode(gw: number, branch: number, type: 'APPROVAL' | 'CC') {
  const target = nodes.value[gw]?.branches?.[branch]
  if (!target) return
  const node: FlowNode = type === 'CC'
    ? { id: 'c' + Date.now().toString(36), type: 'CC', name: '抄送', props: { ccUsers: '' } }
    : newApproval()
  ;(target.nodes ??= []).push(node)
}

// ---------- 条件网关编辑 ----------
const gatewayVisible = ref(false)
const gatewayIndex = ref(-1)
const gatewayDraft = ref<FlowNode | null>(null)
const branchModes = reactive<Record<number, 'rule' | 'expr'>>({})
const branchRules = reactive<Record<number, RuleRow[]>>({})

const TERM_PATTERN = /^([\w\u4e00-\u9fa5.]+)\s*(>=|<=|==|!=|>|<)\s*('(?:[^'\\]|\\.)*'|"(?:[^"\\]|\\.)*"|[^&\s]+)$/

function parseRules(condition?: string): RuleRow[] | null {
  if (!condition?.trim()) return []
  const terms = condition.split('&&').map((t) => t.trim()).filter(Boolean)
  const rules: RuleRow[] = []
  for (const term of terms) {
    const m = TERM_PATTERN.exec(term)
    if (!m) return null
    rules.push({ field: m[1], op: m[2], value: unquote(m[3]) })
  }
  return rules
}

function unquote(raw: string): string {
  if ((raw.startsWith("'") && raw.endsWith("'")) || (raw.startsWith('"') && raw.endsWith('"'))) {
    return raw.slice(1, -1).replace(/\\'/g, "'").replace(/\\"/g, '"')
  }
  return raw
}

function literal(value: string): string {
  const trimmed = value.trim()
  if (trimmed !== '' && !Number.isNaN(Number(trimmed))) return trimmed
  if (trimmed === 'true' || trimmed === 'false') return trimmed
  return "'" + trimmed.replace(/'/g, "\\'") + "'"
}

function buildExpression(rules: RuleRow[]): string {
  return rules
    .filter((r) => r.field.trim() && r.value.trim())
    .map((r) => `${r.field.trim()} ${r.op} ${literal(r.value)}`)
    .join(' && ')
}

function openGatewayEdit(index: number) {
  const node = nodes.value[index]
  if (!canEditNode(node)) return
  gatewayIndex.value = index
  selectedIndex.value = index
  gatewayDraft.value = JSON.parse(JSON.stringify(node))
  const draftBranches = gatewayDraft.value!.branches ??= []
  draftBranches.forEach((b, bi) => {
    const rules = parseRules(b.condition)
    if (rules === null) {
      branchModes[bi] = 'expr'
      branchRules[bi] = []
    } else {
      branchModes[bi] = 'rule'
      branchRules[bi] = rules
    }
  })
  gatewayVisible.value = true
}

function syncRulesToCondition(bi: number) {
  if (branchModes[bi] !== 'rule') return
  const draft = gatewayDraft.value?.branches?.[bi]
  if (draft) draft.condition = buildExpression(branchRules[bi] ?? [])
}

function addRule(bi: number) {
  syncRulesToCondition(bi)
  ;(branchRules[bi] ??= []).push({ field: '', op: '>', value: '' })
}

function removeRule(bi: number, ri: number) {
  branchRules[bi]?.splice(ri, 1)
  syncRulesToCondition(bi)
}

function addGatewayBranch() {
  const draft = gatewayDraft.value
  if (!draft) return
  const branches = (draft.branches ??= [])
  const last = branches[branches.length - 1]
  if (last && !last.condition?.trim()) {
    // 原默认分支补一个空条件占位，提示用户填写
    ElMessage.warning('请先给当前默认分支填写条件，再追加新分支')
    return
  }
  branches.splice(branches.length - 1, 0, { name: `分支${branches.length}`, condition: '', nodes: [newApproval(`分支${branches.length}审批`)] })
  const bi = branches.length - 2
  branchModes[bi] = 'rule'
  branchRules[bi] = []
}

function removeGatewayBranch(bi: number) {
  gatewayDraft.value?.branches?.splice(bi, 1)
}

function validateGatewayDraft(): boolean {
  const draft = gatewayDraft.value
  if (!draft) return false
  if (!draft.name?.trim()) {
    ElMessage.warning('网关名称不能为空')
    return false
  }
  const branches = draft.branches ?? []
  if (branches.length < 2) {
    ElMessage.warning('至少需要 2 个分支')
    return false
  }
  const blankIdx = branches.map((b, i) => (b.condition?.trim() ? -1 : i)).filter((i) => i >= 0)
  if (blankIdx.length !== 1 || blankIdx[0] !== branches.length - 1) {
    ElMessage.warning('必须且只能有一个默认分支（条件留空），并且放在最后')
    return false
  }
  for (let i = 0; i < branches.length; i++) {
    if (!branches[i].name?.trim()) {
      ElMessage.warning(`分支 ${i + 1} 名称不能为空`)
      return false
    }
    if (!(branches[i].nodes ?? []).length) {
      ElMessage.warning(`分支「${branches[i].name}」内至少需要 1 个节点`)
      return false
    }
  }
  return true
}

function saveGateway() {
  if (!validateGatewayDraft()) return
  const draft = gatewayDraft.value!
  draft.branches?.forEach((_, bi) => syncRulesToCondition(bi))
  nodes.value[gatewayIndex.value] = JSON.parse(JSON.stringify(draft))
  gatewayVisible.value = false
  gatewayDraft.value = null
}

// ---------- 条件试跑 ----------
const testVisible = ref(false)
const testing = ref(false)
const testFormData = ref('{\n  "amount": 15000,\n  "category": "travel"\n}')
const testResult = ref<{ matched: boolean[]; firstMatchIndex: number } | null>(null)

function openTestDialog() {
  // 先把当前草稿条件写入预览
  gatewayDraft.value?.branches?.forEach((_, bi) => syncRulesToCondition(bi))
  testResult.value = null
  testVisible.value = true
}

async function runTest() {
  const draft = gatewayDraft.value
  if (!draft) return
  let formData: Record<string, unknown>
  try {
    formData = JSON.parse(testFormData.value || '{}')
  } catch {
    ElMessage.warning('样例数据不是合法 JSON')
    return
  }
  testing.value = true
  try {
    testResult.value = await conditionTest(code.value,
      (draft.branches ?? []).map((b) => b.condition ?? ''), formData)
  } finally {
    testing.value = false
  }
}

// ---------- 保存与删除 ----------
function removeNode() {
  if (!editing.value) return
  const ctx = editCtx.value
  if (ctx.scope === 'main') {
    const index = nodes.value.indexOf(editing.value)
    if (index >= 0) nodes.value.splice(index, 1)
  } else {
    nodes.value[ctx.gw]?.branches?.[ctx.branch]?.nodes?.splice(ctx.node, 1)
  }
  drawerVisible.value = false
  editing.value = null
  selectedIndex.value = -1
}

function validateNodeCommon(node: FlowNode): boolean {
  if (!node.name?.trim()) {
    ElMessage.warning(`「${nodeLabel(node.type)}」节点名称不能为空`)
    return false
  }
  if (node.type === 'APPROVAL') {
    const emptyList = (node.props?.approverType === 'SPECIFIED' && !splitList(node.props.specifiedUsers ?? '').length)
      || (node.props?.approverType === 'ROLE' && !splitList(node.props.roleCodes ?? '').length)
    if (emptyList) {
      ElMessage.warning(`审批节点「${node.name}」未指定审批人`)
      return false
    }
    if (node.props?.approverType === 'FORM_FIELD' && !node.props.formFieldKey?.trim()) {
      ElMessage.warning(`审批节点「${node.name}」未填写表单字段`)
      return false
    }
  }
  if (node.type === 'CC' && !splitList(node.props?.ccUsers ?? '').length) {
    ElMessage.warning(`抄送节点「${node.name}」未配置抄送人`)
    return false
  }
  return true
}

async function saveDraft(): Promise<boolean> {
  if (!flow.value) return false
  for (const node of nodes.value) {
    if (!validateNodeCommon(node)) return false
    if (node.type === 'CONDITION') {
      const branches = node.branches ?? []
      if (branches.length < 2) {
        ElMessage.warning(`条件分支「${node.name}」至少需要 2 个分支`)
        return false
      }
      const blankIdx = branches.map((b, i) => (b.condition?.trim() ? -1 : i)).filter((i) => i >= 0)
      if (blankIdx.length !== 1 || blankIdx[0] !== branches.length - 1) {
        ElMessage.warning(`条件分支「${node.name}」必须且只能有一个默认分支（条件留空），并放在最后`)
        return false
      }
      for (const branch of branches) {
        if (!branch.name?.trim()) {
          ElMessage.warning(`条件分支「${node.name}」存在未命名的分支`)
          return false
        }
        if (!(branch.nodes ?? []).length) {
          ElMessage.warning(`分支「${branch.name}」内至少需要 1 个节点`)
          return false
        }
        for (const child of branch.nodes ?? []) {
          if (!validateNodeCommon(child)) return false
        }
      }
    }
  }
  await saveFlow({
    code: flow.value.code,
    name: flow.value.name,
    category: flow.value.category,
    icon: flow.value.icon,
    description: flow.value.description,
    formSchema: flow.value.formSchema ?? undefined,
    flowSchema: { nodes: nodes.value.map((n) => serializeNode(n)) },
  })
  return true
}

async function onSave() {
  if (await saveDraft()) ElMessage.success('已保存为草稿，发布后生效')
}

const publishing = ref(false)

async function onPublish() {
  publishing.value = true
  try {
    if (!(await saveDraft())) return
    const { value } = await ElMessageBox.prompt('发布说明（可选）', `发布 ${flow.value?.name ?? ''}`, {
      inputPlaceholder: '本次发布改了什么',
      confirmButtonText: '确认发布',
      cancelButtonText: '仅保留草稿',
    })
    await publishFlow(code.value, value || undefined)
    ElMessage.success('发布成功，新审批即刻按新版本流转')
    flow.value = await fetchFlow(code.value)
  } catch (e) {
    // 取消发布弹窗（reject 为字符串）：草稿已保存，不算错误；真正的接口异常照常抛出
    if (typeof e !== 'string') throw e
  } finally {
    publishing.value = false
  }
}

function serializeNode(node: FlowNode): Record<string, unknown> {
  const result: Record<string, unknown> = { id: node.id, type: node.type, name: node.name }
  if (node.condition) result.condition = node.condition
  if (node.type === 'CONDITION') {
    result.branches = (node.branches ?? []).map((b) => ({
      name: b.name,
      ...(b.condition?.trim() ? { condition: b.condition.trim() } : {}),
      nodes: (b.nodes ?? []).map((child) => serializeNode(child)),
    }))
    return result
  }
  if (node.type === 'CC' && node.props) {
    result.props = { ccUsers: splitList(node.props.ccUsers ?? '') }
    return result
  }
  if (node.type === 'APPROVAL' && node.props) {
    const props: Record<string, unknown> = {
      approverType: node.props.approverType,
      mode: node.props.mode,
      rejectStrategy: node.props.rejectStrategy,
    }
    if (node.props.emptyStrategy && node.props.emptyStrategy !== 'FAIL') {
      props.emptyStrategy = node.props.emptyStrategy
    }
    if (node.props.leaderLevel != null) props.leaderLevel = node.props.leaderLevel
    if (node.props.specifiedUsers) {
      props.specifiedUsers = splitList(node.props.specifiedUsers)
    }
    if (node.props.roleCodes) {
      props.roleCodes = splitList(node.props.roleCodes)
    }
    if (node.props.formFieldKey) props.formFieldKey = node.props.formFieldKey
    result.props = props
  }
  return result
}

function splitList(value: string | string[]): string[] {
  if (Array.isArray(value)) return value
  return value.split(/[,，\s]+/).filter(Boolean)
}

onMounted(async () => {
  const [loaded, users, roles, fields] = await Promise.all([
    fetchFlow(code.value),
    orgUsers({ page: 1, size: 500 }).catch(() => null),
    orgRoles().catch(() => null),
    flowFormFields(code.value).catch(() => null),
  ])
  flow.value = loaded
  userOptions.value = users?.list ?? []
  roleOptions.value = roles ?? []
  fieldOptions.value = fields ?? []
  const schema = flow.value.flowSchema as { nodes?: FlowNode[] } | null
  const list = schema?.nodes ?? []
  // 编辑态归一化：数组形式的抄送人转为逗号分隔字符串；网关分支兜底
  const normalize = (list: FlowNode[]) => {
    list.forEach((n) => {
      if (n.props && Array.isArray(n.props.ccUsers)) {
        n.props.ccUsers = n.props.ccUsers.join(',')
      }
      if (n.type === 'CONDITION') {
        n.branches ??= []
        n.branches.forEach((b) => {
          b.nodes ??= []
          normalize(b.nodes)
        })
      }
    })
  }
  normalize(list)
  nodes.value = list
})
</script>

<style scoped>
.toolbar { display: flex; align-items: center; gap: 8px; margin-bottom: 16px; }
.flow-title { font-weight: 600; }
.designer { max-width: 420px; margin: 0 auto; padding: 24px 0; }
.node { border: 1px solid #dcdfe6; border-radius: 10px; padding: 12px 16px; cursor: pointer; background: #fff; transition: all 0.2s; }
.node.editable:hover { border-color: #0960bd; transform: translateY(-1px); box-shadow: 0 4px 12px rgba(9, 96, 189, 0.12); }
.node.selected { border-color: #0960bd; box-shadow: 0 0 0 2px rgba(9, 96, 189, 0.18); }
.node.start, .node.end { text-align: center; position: relative; }
.node.start { background: linear-gradient(135deg, #f0fbf2, #dff3e5); border: 1.5px solid #67c23a; }
.node.start .node-type { color: #529b2e; }
.node.start .node-name::before { content: '▶ '; color: #67c23a; font-size: 12px; }
.node.end { background: linear-gradient(135deg, #f5f7fa, #e8ebf0); border: 1.5px solid #909399; }
.node.end .node-type { color: #73767c; }
.node.end .node-name::after { content: ' ■'; color: #909399; font-size: 11px; }
.fixed-tag { position: absolute; top: -10px; right: 8px; font-size: 10px; line-height: 1; padding: 3px 6px; border-radius: 8px; background: #e6e8eb; color: #73767c; }
.node.approval { background: #eef5ff; }
.node.cc { background: #f0f9eb; }
.node-type { font-size: 12px; color: #909399; margin-bottom: 4px; }
.node-name { font-weight: 600; font-size: 15px; }
.node-name.small { font-size: 13px; }
.node-props { font-size: 12px; color: #606266; margin-top: 4px; }
.connector { display: flex; flex-direction: column; align-items: center; margin: 3px 0; }
.connector .seg { width: 2px; height: 12px; background: #c8ccd4; }
.connector .seg.short { height: 6px; }
.connector .head { width: 0; height: 0; border-left: 5px solid transparent; border-right: 5px solid transparent; border-top: 6px solid #c8ccd4; }
.connector .insert-btn { margin: 0; font-weight: 600; color: #0960bd; border-color: #bcd4ee; }
.connector .insert-btn:hover { background: #ecf3fb; }
.field-hint { font-size: 12px; color: #909399; line-height: 1.5; margin-top: 4px; }

.node.gateway { background: #f6f2ff; border: 1.5px solid #a8a2e8; }
.gateway-branches { display: flex; gap: 10px; margin-top: 10px; }
.branch-panel { flex: 1; min-width: 0; border: 1px dashed #b4b2c8; border-radius: 8px; padding: 8px; background: #fff; }
.branch-panel.branch-default { border-style: solid; border-color: #c8ccd4; background: #fafafa; }
.branch-head { display: flex; align-items: center; gap: 6px; margin-bottom: 6px; flex-wrap: wrap; }
.branch-name { font-weight: 600; font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.branch-node { padding: 8px 10px; }
.branch-arrow { text-align: center; color: #c0c4cc; font-size: 12px; line-height: 1.2; }
.branch-add { display: flex; gap: 4px; margin-top: 6px; }
.branch-empty { font-size: 12px; color: #c0c4cc; text-align: center; padding: 8px 0; }

.gw-branch { border: 1px solid #e4e7ed; border-radius: 8px; padding: 12px 12px 4px; margin-bottom: 12px; }
.gw-branch-head { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; }
.gw-branch-title { font-weight: 600; }
.cond-editor { width: 100%; }
.rule-row { display: flex; align-items: center; gap: 6px; margin-bottom: 6px; }
.test-result { margin-top: 12px; }
.test-row { display: flex; align-items: center; justify-content: space-between; padding: 4px 0; font-size: 13px; }
</style>
