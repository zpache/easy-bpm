<template>
  <el-card>
    <div class="toolbar">
      <el-tabs v-model="tab" style="flex: 1" @tab-change="onTab">
        <el-tab-pane label="待办" name="todo" />
        <el-tab-pane label="已办" name="done" />
      </el-tabs>
      <el-input v-model="keyword" placeholder="按标题搜索" clearable style="width: 240px" @keyup.enter="load" @clear="load" />
      <el-button style="margin-left: 8px" @click="load">搜索</el-button>
    </div>

    <el-table :data="tasks" v-loading="loading">
      <el-table-column prop="instanceTitle" label="审批标题" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">
          <el-link type="primary" @click="goDetail(row.processInstanceId)">{{ row.instanceTitle }}</el-link>
        </template>
      </el-table-column>
      <el-table-column prop="flowName" label="流程" width="150" />
      <el-table-column prop="nodeName" label="节点" width="140" />
      <el-table-column prop="initiatorName" label="发起人" width="110" />
      <el-table-column prop="assigneeName" label="处理人" width="110" />
      <el-table-column v-if="tab === 'todo'" prop="createTime" label="到达时间" width="180" />
      <el-table-column v-else prop="endTime" label="处理时间" width="180" />
      <el-table-column v-if="tab === 'done'" label="操作" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="row.action === 'REJECT' ? 'danger' : row.action === 'TRANSFER' ? 'warning' : 'success'">
            {{ actionText(row.action) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="tab === 'todo'" label="操作" width="180">
        <template #default="{ row }">
          <el-button size="small" type="success" @click="handle(row, 'approve')">同意</el-button>
          <el-button size="small" type="danger" @click="handle(row, 'reject')">驳回</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-model:current-page="page"
      :page-size="size"
      :total="total"
      layout="total, prev, pager, next"
      style="margin-top: 12px; justify-content: flex-end"
      @current-change="load"
    />

    <el-dialog v-model="handleVisible" :title="handleAction === 'approve' ? '同意' : '驳回'" width="440px">
      <el-input v-model="comment" type="textarea" :rows="3" placeholder="审批意见" />
      <template #footer>
        <el-button @click="handleVisible = false">取消</el-button>
        <el-button :type="handleAction === 'approve' ? 'success' : 'danger'" @click="submitHandle">确认</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { approveTask, doneTasks, rejectTask, todoTasks } from '@/api/workbench'
import type { TaskView } from '@/api/types'
import { actionText } from '@/utils/labels'

const route = useRoute()
const router = useRouter()
const tab = ref((route.meta.tab as string) || 'todo')
const tasks = ref<TaskView[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const loading = ref(false)
const handleVisible = ref(false)
const handleAction = ref<'approve' | 'reject'>('approve')
const handlingTask = ref<TaskView | null>(null)
const comment = ref('')

async function load() {
  loading.value = true
  try {
    const params = { page: page.value, size: size.value, keyword: keyword.value || undefined }
    const result = tab.value === 'todo' ? await todoTasks(params) : await doneTasks(params)
    tasks.value = result.list
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function onTab(name: number | string) {
  router.replace({ name: name === 'todo' ? 'todo' : 'done' })
  page.value = 1
  load()
}

function handle(row: TaskView, action: 'approve' | 'reject') {
  handlingTask.value = row
  handleAction.value = action
  comment.value = ''
  handleVisible.value = true
}

async function submitHandle() {
  if (!handlingTask.value) return
  if (handleAction.value === 'approve') {
    await approveTask(handlingTask.value.taskId, comment.value)
    ElMessage.success('已同意')
  } else {
    await rejectTask(handlingTask.value.taskId, comment.value)
    ElMessage.success('已驳回')
  }
  handleVisible.value = false
  await load()
}

function goDetail(instanceId: string) {
  router.push({ name: 'instance-detail', params: { instanceId } })
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; align-items: center; margin-bottom: 8px; }
</style>
