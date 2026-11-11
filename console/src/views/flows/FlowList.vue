<template>
  <el-card>
    <div class="toolbar">
      <el-button v-if="auth.isTenantAdmin" type="primary" @click="openCreate">新建流程</el-button>
    </div>
    <el-table :data="flows" v-loading="loading">
      <template #empty><el-empty description="暂无流程定义" :image-size="80" /></template>
      <el-table-column prop="code" label="编码" width="140" />
      <el-table-column prop="name" label="名称" min-width="160" />
      <el-table-column prop="category" label="分类" width="110" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="version" label="版本" width="70" />
      <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
      <el-table-column prop="updatedAt" label="更新时间" width="180">
        <template #default="{ row }">{{ fmtDateTime(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="goDesign(row)">设计审批流程</el-button>
          <template v-if="auth.isTenantAdmin">
            <el-button v-if="row.status === 'PUBLISHED'" link type="warning" @click="onDisable(row)">停用</el-button>
            <el-button v-else-if="row.status === 'DISABLED'" link type="success" @click="onEnable(row)">启用</el-button>
          </template>
          <el-button link type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <TablePagination
      v-model:current-page="page"
      v-model:page-size="size"
      :total="total"
      :loading="loading"
      @change="load"
    />

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑流程' : '新建流程'" width="520px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="编码">
          <el-input v-model="form.code" :disabled="isEdit" placeholder="如 leave" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="分类">
          <el-input v-model="form.category" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import TablePagination from '@/components/TablePagination.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteFlow, disableFlow, enableFlow, pageFlows, saveFlow } from '@/api/flows'
import { useAuthStore } from '@/stores/auth'
import { statusText, statusType } from '@/utils/labels'
import { fmtDateTime } from '@/utils/format'
import type { FlowDefinition } from '@/api/types'

const router = useRouter()
const auth = useAuthStore()
const flows = ref<FlowDefinition[]>([])
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const dialogVisible = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const form = reactive({ code: '', name: '', category: '', description: '' })
let loadSequence = 0

async function load() {
  const sequence = ++loadSequence
  loading.value = true
  try {
    const result = await pageFlows(page.value, size.value)
    if (sequence !== loadSequence) return
    const lastPage = Math.max(1, Math.ceil(result.total / size.value))
    if (page.value > lastPage) {
      page.value = lastPage
      await load()
      return
    }
    flows.value = result.list
    total.value = result.total
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}

async function reload() {
  page.value = 1
  await load()
}

function openCreate() {
  isEdit.value = false
  Object.assign(form, { code: '', name: '', category: '', description: '' })
  dialogVisible.value = true
}

async function onSave() {
  saving.value = true
  try {
    await saveFlow({
      code: form.code,
      name: form.name,
      category: form.category,
      description: form.description,
      flowSchema: defaultSchema(),
      formSchema: undefined,
    })
    ElMessage.success('已保存（草稿），可在操作列进入设计审批流程')
    dialogVisible.value = false
    await reload()
  } finally {
    saving.value = false
  }
}

function defaultSchema() {
  return {
    nodes: [
      { id: 'start', type: 'START', name: '发起人' },
      { id: 'n1', type: 'APPROVAL', name: '审批人', props: { approverType: 'INITIATOR_LEADER', leaderLevel: 1, mode: 'ANY' } },
      { id: 'end', type: 'END', name: '结束' },
    ],
  }
}

async function onDisable(row: FlowDefinition) {
  await ElMessageBox.confirm(`确认停用「${row.name}」？运行中实例不受影响，停用后可通过「启用」恢复`, '停用', { type: 'warning' })
  await disableFlow(row.code)
  ElMessage.success('已停用')
  await load()
}

async function onEnable(row: FlowDefinition) {
  const neverPublished = !row.version
  const message = neverPublished
    ? `「${row.name}」从未发布过，启用后将恢复为草稿状态，需进入设计页完成发布`
    : `确认启用「${row.name}」？将恢复到其最后一次发布的版本 v${row.version}`
  await ElMessageBox.confirm(message, '启用', { type: 'info' })
  await enableFlow(row.code)
  ElMessage.success(neverPublished ? '已恢复为草稿，请进入设计页发布' : '已启用')
  await load()
}

async function onDelete(row: FlowDefinition) {
  await ElMessageBox.confirm(`确认删除「${row.name}」？仅未发布过的草稿可删除`, '删除', { type: 'warning' })
  await deleteFlow(row.code)
  ElMessage.success('已删除')
  await load()
}

function goDesign(row: FlowDefinition) {
  router.push({ name: 'flow-design', params: { code: row.code } })
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; gap: 8px; margin-bottom: 12px; }
</style>
