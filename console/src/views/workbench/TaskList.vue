<template>
  <el-card>
    <div class="toolbar">
      <el-tabs v-model="tab" class="toolbar-tabs" @tab-change="onTab">
        <el-tab-pane label="待办" name="todo" />
        <el-tab-pane label="已办" name="done" />
      </el-tabs>
      <div class="toolbar-right">
        <el-input
          v-model="keyword"
          placeholder="按标题搜索"
          clearable
          style="width: 220px"
          :prefix-icon="Search"
          @keyup.enter="reload"
          @clear="reload"
        />
        <el-button @click="reload">
          <el-icon><Refresh /></el-icon>
        </el-button>
      </div>
    </div>

    <el-table :data="tasks" v-loading="loading" stripe @row-click="goDetailRow">
      <template #empty>
        <el-empty :description="tab === 'todo' ? '暂无待办任务' : '暂无已办任务'" :image-size="80" />
      </template>
      <el-table-column prop="instanceTitle" label="审批标题" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">
          <el-link type="primary" :underline="false" @click.stop="goDetail(row.processInstanceId)">
            {{ row.instanceTitle }}
          </el-link>
        </template>
      </el-table-column>
      <el-table-column prop="flowName" label="流程" width="150" show-overflow-tooltip />
      <el-table-column prop="nodeName" label="节点" width="130" show-overflow-tooltip />
      <el-table-column prop="initiatorName" label="发起人" width="100" />
      <el-table-column v-if="tab === 'todo'" prop="assigneeName" label="处理人" width="100" />
      <el-table-column v-if="tab === 'todo'" prop="createTime" label="到达时间" width="165">
        <template #default="{ row }">{{ fmtDateTime(row.createTime) }}</template>
      </el-table-column>
      <template v-else>
        <el-table-column label="耗时" width="100">
          <template #default="{ row }">{{ duration(row.createTime, row.endTime) }}</template>
        </el-table-column>
        <el-table-column prop="endTime" label="处理时间" width="165">
          <template #default="{ row }">{{ fmtDateTime(row.endTime) }}</template>
        </el-table-column>
        <el-table-column label="我的处理" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="actionType(row.action)" effect="light" round>
              {{ actionText(row.action) }}
            </el-tag>
          </template>
        </el-table-column>
      </template>
      <el-table-column v-if="tab === 'todo'" label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="success" plain @click.stop="handle(row, 'approve')">同意</el-button>
          <el-button size="small" type="danger" plain @click.stop="handle(row, 'reject')">驳回</el-button>
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
import TablePagination from '@/components/TablePagination.vue'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { approveTask, doneTasks, rejectTask, todoTasks } from '@/api/workbench'
import type { TaskView } from '@/api/types'
import { actionText, actionType } from '@/utils/labels'
import { fmtDateTime } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const tab = ref((route.meta.tab as string) || 'todo')
const tasks = ref<TaskView[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
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

function reload() {
  page.value = 1
  load()
}

function onTab(name: number | string) {
  router.replace({ name: name === 'todo' ? 'todo' : 'done' })
  reload()
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

function goDetailRow(row: TaskView) {
  goDetail(row.processInstanceId)
}

function duration(start?: string, end?: string) {
  if (!start || !end) return '-'
  const minutes = Math.max(0, Math.round((new Date(end.replace(' ', 'T')).getTime()
    - new Date(start.replace(' ', 'T')).getTime()) / 60000))
  if (minutes < 1) return '1 分钟内'
  if (minutes < 60) return `${minutes} 分钟`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时 ${minutes % 60} 分`
  return `${Math.floor(hours / 24)} 天 ${hours % 24} 小时`
}

onMounted(load)
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border-bottom: 1px solid #ebeef5;
  margin-bottom: 12px;
}
.toolbar-tabs :deep(.el-tabs__header) { margin-bottom: 0; }
.toolbar-right { display: flex; align-items: center; gap: 8px; padding-bottom: 8px; }
:deep(.el-table__row) { cursor: pointer; }
</style>
