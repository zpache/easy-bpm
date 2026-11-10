<template>
  <el-card>
    <div class="toolbar">
      <el-button @click="router.push({ name: 'flows' })">返回</el-button>
      <span class="flow-title">{{ flow?.name }}（{{ code }}）· 审批链设计</span>
      <span style="flex: 1" />
      <el-button v-if="auth.isTenantAdmin" :disabled="flow?.status === 'DISABLED'" @click="addApprovalNode">+ 审批节点</el-button>
      <el-button v-if="auth.isTenantAdmin" :disabled="flow?.status === 'DISABLED'" @click="addCcNode">+ 抄送节点</el-button>
      <el-button v-if="auth.isTenantAdmin" type="primary" :disabled="flow?.status === 'DISABLED'" @click="onSave">保存草稿</el-button>
    </div>

    <el-alert v-if="flow?.status === 'DISABLED'" type="warning" :closable="false" style="margin-bottom: 12px">
      流程已停用，仅可查看。
    </el-alert>

    <div class="designer">
      <template v-for="(node, index) in nodes" :key="node.id + index">
        <div class="node" :class="[nodeClass(node), { selected: selectedIndex === index }]" @click="selectedIndex = index">
          <div class="node-type">{{ nodeLabel(node.type) }}</div>
          <div class="node-name">{{ node.name }}</div>
          <div v-if="node.type === 'APPROVAL' && node.props" class="node-props">
            {{ approverText(node.props) }} · {{ node.props.mode === 'ALL' ? '会签' : '或签' }}
            <template v-if="node.props.rejectStrategy === 'TO_START'"> · 驳回退回发起人</template>
          </div>
          <div v-if="node.type === 'CC' && node.props" class="node-props">
            抄送给：{{ ccText(node.props) }}
          </div>
          <el-tag v-if="node.condition" size="small" type="warning" style="margin-top: 6px">条件：{{ node.condition }}</el-tag>
        </div>
        <div v-if="canInsertAfter(index)" class="arrow">
          <el-button circle size="small" @click.stop="insertAfter(index)">＋</el-button>
        </div>
      </template>
    </div>

    <el-drawer v-model="drawerVisible" :title="editing ? '编辑节点' : '新增节点'" size="440px">
      <el-form v-if="editing" :model="editing" label-width="100px">
        <el-form-item label="节点名称">
          <el-input v-model="editing.name" />
        </el-form-item>
        <el-form-item v-if="editing.type === 'CC'" label="抄送人">
          <el-input v-model="ccUsersInput" placeholder="逗号分隔 userCode" />
        </el-form-item>
        <el-form-item v-if="editing.type !== 'CC'" label="条件表达式">
          <el-input v-model="editing.condition" placeholder="如 days > 3，留空表示无条件" />
        </el-form-item>
        <template v-if="editing.type === 'APPROVAL'">
          <el-form-item label="审批人类型">
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
          <el-form-item v-if="editing.props!.approverType === 'SPECIFIED'" label="指定人员">
            <el-input v-model="editing.props!.specifiedUsers" placeholder="逗号分隔 userCode" />
          </el-form-item>
          <el-form-item v-if="editing.props!.approverType === 'ROLE'" label="角色编码">
            <el-input v-model="editing.props!.roleCodes" placeholder="逗号分隔角色编码" />
          </el-form-item>
          <el-form-item v-if="editing.props!.approverType === 'FORM_FIELD'" label="表单字段">
            <el-input v-model="editing.props!.formFieldKey" placeholder="值为 userCode 的字段名" />
          </el-form-item>
          <el-form-item label="审批方式">
            <el-select v-model="editing.props!.mode">
              <el-option label="或签（一人通过即过）" value="ANY" />
              <el-option label="会签（全部通过才过）" value="ALL" />
            </el-select>
          </el-form-item>
          <el-form-item label="驳回策略">
            <el-select v-model="editing.props!.rejectStrategy">
              <el-option label="驳回到结束" value="TO_END" />
              <el-option label="退回发起人重提" value="TO_START" />
            </el-select>
          </el-form-item>
        </template>
        <el-button type="danger" plain style="width: 100%" @click="removeNode">删除该节点</el-button>
      </el-form>
    </el-drawer>
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchFlow, saveFlow } from '@/api/flows'
import { useAuthStore } from '@/stores/auth'

interface NodeProps {
  approverType?: string
  leaderLevel?: number
  specifiedUsers?: string | string[]
  roleCodes?: string | string[]
  formFieldKey?: string
  mode?: string
  rejectStrategy?: string
  ccUsers?: string | string[]
}

interface FlowNode {
  id: string
  type: 'START' | 'APPROVAL' | 'CC' | 'END'
  name: string
  condition?: string
  props?: NodeProps
}

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const code = computed(() => route.params.code as string)
const flow = ref<Awaited<ReturnType<typeof fetchFlow>> | null>(null)
const nodes = ref<FlowNode[]>([])
const selectedIndex = ref(-1)
const drawerVisible = ref(false)
const editing = ref<(FlowNode & { props: NodeProps }) | null>(null)

function nodeClass(node: FlowNode) {
  return { start: node.type === 'START', approval: node.type === 'APPROVAL', cc: node.type === 'CC', end: node.type === 'END' }
}

function nodeLabel(type: FlowNode['type']) {
  return { START: '发起', APPROVAL: '审批', CC: '抄送', END: '结束' }[type]
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

function removeNode() {
  if (!editing.value) return
  const index = nodes.value.indexOf(editing.value)
  if (index >= 0) nodes.value.splice(index, 1)
  drawerVisible.value = false
  editing.value = null
  selectedIndex.value = -1
}

async function onSave() {
  if (!flow.value) return
  const emptyCc = nodes.value.find((n) => n.type === 'CC' && !splitList(n.props?.ccUsers ?? '').length)
  if (emptyCc) {
    ElMessage.warning(`抄送节点「${emptyCc.name}」未配置抄送人`)
    return
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
  ElMessage.success('已保存为草稿，发布后生效')
}

function serializeNode(node: FlowNode): Record<string, unknown> {
  const result: Record<string, unknown> = { id: node.id, type: node.type, name: node.name }
  if (node.condition) result.condition = node.condition
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
  flow.value = await fetchFlow(code.value)
  const schema = flow.value.flowSchema as { nodes?: FlowNode[] } | null
  const list = schema?.nodes ?? []
  // 编辑态归一化：数组形式的抄送人转为逗号分隔字符串
  list.forEach((n) => {
    if (n.props && Array.isArray(n.props.ccUsers)) {
      n.props.ccUsers = n.props.ccUsers.join(',')
    }
  })
  nodes.value = list
})
</script>

<style scoped>
.toolbar { display: flex; align-items: center; gap: 8px; margin-bottom: 16px; }
.flow-title { font-weight: 600; }
.designer { max-width: 420px; margin: 0 auto; padding: 24px 0; }
.node { border: 1px solid #dcdfe6; border-radius: 10px; padding: 12px 16px; cursor: pointer; background: #fff; transition: all 0.2s; }
.node:hover { border-color: #0960bd; transform: translateY(-1px); box-shadow: 0 4px 12px rgba(9, 96, 189, 0.12); }
.node.selected { border-color: #0960bd; box-shadow: 0 0 0 2px rgba(9, 96, 189, 0.18); }
.node.start, .node.end { background: #f4f7fb; text-align: center; }
.node.approval { background: #eef5ff; }
.node.cc { background: #f0f9eb; }
.node-type { font-size: 12px; color: #909399; margin-bottom: 4px; }
.node-name { font-weight: 600; font-size: 15px; }
.node-props { font-size: 12px; color: #606266; margin-top: 4px; }
.arrow { text-align: center; padding: 8px 0; color: #c0c4cc; }
</style>
