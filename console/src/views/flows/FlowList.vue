<template>
  <el-card>
    <div class="toolbar">
      <el-button v-if="auth.isTenantAdmin" type="primary" @click="openCreate">新建流程</el-button>
      <el-button :disabled="!selected" @click="goDesign">设计审批链</el-button>
      <el-button :disabled="!selected" @click="goVersions">版本历史</el-button>
      <el-button type="success" :disabled="!selected" @click="onPublish">发布</el-button>
      <el-button type="warning" :disabled="!selected" @click="onDisable">停用</el-button>
      <el-button type="danger" :disabled="!selected" @click="onDelete">删除</el-button>
    </div>
    <el-table :data="flows" highlight-current-row @current-change="(row?: FlowDefinition) => (selected = row ?? null)">
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
      <el-table-column prop="updatedAt" label="更新时间" width="180" />
    </el-table>

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
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteFlow, disableFlow, listFlows, publishFlow, saveFlow } from '@/api/flows'
import { useAuthStore } from '@/stores/auth'
import { statusText, statusType } from '@/utils/labels'
import type { FlowDefinition } from '@/api/types'

const router = useRouter()
const auth = useAuthStore()
const flows = ref<FlowDefinition[]>([])
const selected = ref<FlowDefinition | null>(null)
const dialogVisible = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const form = reactive({ code: '', name: '', category: '', description: '' })

async function load() {
  flows.value = await listFlows()
}

function openCreate() {
  isEdit.value = false
  Object.assign(form, { code: '', name: '', category: '', description: '' })
  dialogVisible.value = true
}

function openEdit() {
  if (!selected.value) return
  isEdit.value = true
  Object.assign(form, {
    code: selected.value.code,
    name: selected.value.name,
    category: selected.value.category ?? '',
    description: selected.value.description ?? '',
  })
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
      flowSchema: isEdit.value && selected.value?.flowSchema
        ? selected.value.flowSchema
        : defaultSchema(),
      formSchema: isEdit.value ? selected.value?.formSchema : undefined,
    })
    ElMessage.success('已保存（草稿）')
    dialogVisible.value = false
    await load()
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

async function onPublish() {
  if (!selected.value) return
  const { value } = await ElMessageBox.prompt('发布说明（可选）', `发布 ${selected.value.name}`, {
    inputPlaceholder: '本次发布改了什么',
  })
  await publishFlow(selected.value.code, value || undefined)
  ElMessage.success('发布成功')
  await load()
}

async function onDisable() {
  if (!selected.value) return
  await ElMessageBox.confirm(`确认停用「${selected.value.name}」？运行中实例不受影响`, '停用', { type: 'warning' })
  await disableFlow(selected.value.code)
  ElMessage.success('已停用')
  await load()
}

async function onDelete() {
  if (!selected.value) return
  await ElMessageBox.confirm(`确认删除「${selected.value.name}」？仅未发布过的草稿可删除`, '删除', { type: 'warning' })
  await deleteFlow(selected.value.code)
  ElMessage.success('已删除')
  selected.value = null
  await load()
}

function goDesign() {
  if (selected.value) router.push({ name: 'flow-design', params: { code: selected.value.code } })
}

function goVersions() {
  if (selected.value) router.push({ name: 'flow-versions', params: { code: selected.value.code } })
}

onMounted(load)

defineExpose({ openEdit })
</script>

<style scoped>
.toolbar { display: flex; gap: 8px; margin-bottom: 12px; }
</style>
