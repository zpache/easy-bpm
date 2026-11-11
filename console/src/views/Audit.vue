<template>
  <el-card>
    <div class="toolbar">
      <el-select v-model="action" placeholder="全部操作" clearable style="width: 180px" @change="search">
        <el-option v-for="item in actionOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-input v-model="keyword" placeholder="目标 / 操作人 / 详情" clearable style="width: 220px; margin-left: 8px"
        @keyup.enter="search" @clear="search" />
      <el-button style="margin-left: 8px" @click="search">搜索</el-button>
    </div>
    <el-table :data="logs" v-loading="loading">
      <template #empty><el-empty description="暂无审计记录" :image-size="80" /></template>
      <el-table-column prop="createdAt" label="时间" width="180">
        <template #default="{ row }">{{ fmtDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column prop="action" label="操作" width="150">
        <template #default="{ row }">
          <el-tag size="small" effect="light" round :type="actionTagType(row.action)">{{ actionLabel(row.action) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="targetId" label="目标" width="180" show-overflow-tooltip />
      <el-table-column prop="detail" label="详情" min-width="260" show-overflow-tooltip />
      <el-table-column prop="operatorName" label="操作人" width="110">
        <template #default="{ row }">{{ row.operatorName || row.operatorId }}</template>
      </el-table-column>
      <el-table-column prop="traceId" label="TraceId" width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.traceId || '-' }}</template>
      </el-table-column>
    </el-table>
    <TablePagination
      v-model:current-page="page"
      v-model:page-size="size"
      :total="total"
      :loading="loading"
      @change="load"
    />
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import TablePagination from '@/components/TablePagination.vue'
import { fmtDateTime } from '@/utils/format'
import { auditLogs } from '@/api/audit'
import type { AuditView } from '@/api/types'

const actionOptions = [
  { value: 'FLOW_SAVE', label: '流程保存' },
  { value: 'FLOW_PUBLISH', label: '流程发布' },
  { value: 'FLOW_DISABLE', label: '流程停用' },
  { value: 'FLOW_DELETE', label: '流程删除' },
  { value: 'FLOW_ROLLBACK', label: '版本回滚' },
  { value: 'CREDENTIAL_CREATE', label: '凭证新建' },
  { value: 'CREDENTIAL_ROTATE', label: '凭证轮换' },
  { value: 'CREDENTIAL_DISABLE', label: '凭证停用' },
]

const logs = ref<AuditView[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const action = ref('')
const keyword = ref('')
const loading = ref(false)

function actionLabel(action?: string) {
  return actionOptions.find((o) => o.value === action)?.label ?? action ?? '-'
}

function actionTagType(action?: string): 'primary' | 'success' | 'danger' | 'warning' | 'info' {
  switch (action) {
    case 'FLOW_PUBLISH': case 'CREDENTIAL_CREATE': return 'primary'
    case 'FLOW_DISABLE': case 'CREDENTIAL_ROTATE': return 'warning'
    case 'FLOW_DELETE': case 'CREDENTIAL_DISABLE': return 'danger'
    default: return 'info'
  }
}

async function load() {
  loading.value = true
  try {
    const result = await auditLogs({
      page: page.value,
      size: size.value,
      action: action.value || undefined,
      keyword: keyword.value || undefined,
    })
    logs.value = result.list
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; align-items: center; margin-bottom: 12px; }
</style>
