<template>
  <div>
    <el-card>
      <template #header>
        <div class="toolbar">
          <span>全部实例（本租户）</span>
          <div class="filters">
            <el-select v-model="flowCode" placeholder="全部流程" clearable filterable style="width: 180px">
              <el-option v-for="f in flows" :key="f.code" :label="f.name" :value="f.code" />
            </el-select>
            <el-select v-model="status" placeholder="全部状态" clearable style="width: 130px">
              <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
            </el-select>
            <el-input
              v-model="keyword"
              placeholder="搜索标题 / 发起人"
              clearable
              style="width: 220px"
              :prefix-icon="Search"
              @keyup.enter="reload"
              @clear="reload"
            />
            <el-button type="primary" @click="reload">查询</el-button>
          </div>
        </div>
      </template>

      <el-table :data="rows" v-loading="loading" @row-click="openDetail">
        <template #empty><el-empty description="暂无审批实例" :image-size="80" /></template>
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link type="primary" :underline="false">{{ row.title }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="flowName" label="流程" width="150" show-overflow-tooltip />
        <el-table-column prop="initiatorName" label="发起人" width="100" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small" effect="light" round>{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="currentNodeName" label="当前节点" width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.status === 'RUNNING' ? (row.currentNodeName || '-') : '-' }}</template>
        </el-table-column>
        <el-table-column prop="startedAt" label="发起时间" width="170">
          <template #default="{ row }">{{ fmt(row.startedAt) }}</template>
        </el-table-column>
        <el-table-column prop="endedAt" label="结束时间" width="170">
          <template #default="{ row }">{{ fmt(row.endedAt) }}</template>
        </el-table-column>
        <el-table-column prop="businessKey" label="业务单号" width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.businessKey || '-' }}</template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="load"
          @size-change="reload"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import { allInstances } from '@/api/workbench'
import { listFlows } from '@/api/flows'
import { statusText, statusType } from '@/utils/labels'
import type { FlowDefinition, InstanceView } from '@/api/types'

const router = useRouter()

const flows = ref<FlowDefinition[]>([])
const rows = ref<InstanceView[]>([])
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const flowCode = ref('')
const status = ref('')
const keyword = ref('')

const statusOptions = [
  { value: 'RUNNING', label: '进行中' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'CANCELED', label: '已撤回' },
]

async function load() {
  loading.value = true
  try {
    const result = await allInstances({
      page: page.value,
      size: size.value,
      flowCode: flowCode.value || undefined,
      status: status.value || undefined,
      keyword: keyword.value || undefined,
    })
    rows.value = result.list
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function reload() {
  page.value = 1
  load()
}

function openDetail(row: InstanceView) {
  router.push(`/instances/${row.instanceId}`)
}

function fmt(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}

onMounted(async () => {
  load()
  listFlows().then((list) => (flows.value = list)).catch(() => {})
})
</script>

<style scoped>
.toolbar { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 8px; }
.filters { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.pager { display: flex; justify-content: flex-end; margin-top: 14px; }
:deep(.el-table__row) { cursor: pointer; }
</style>
