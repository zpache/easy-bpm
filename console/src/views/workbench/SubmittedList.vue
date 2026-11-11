<template>
  <el-card>
    <div class="toolbar">
      <el-select v-model="status" placeholder="全部状态" clearable style="width: 140px" @change="reload">
        <el-option label="进行中" value="RUNNING" />
        <el-option label="已通过" value="APPROVED" />
        <el-option label="已驳回" value="REJECTED" />
        <el-option label="已撤回" value="CANCELED" />
      </el-select>
      <el-input v-model="keyword" placeholder="按标题搜索" clearable style="width: 220px" :prefix-icon="Search" @keyup.enter="reload" @clear="reload" />
      <el-button type="primary" @click="reload">查询</el-button>
    </div>

    <el-table :data="instances" v-loading="loading">
      <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">
          <el-link type="primary" @click="goDetail(row.instanceId)">{{ row.title }}</el-link>
        </template>
      </el-table-column>
      <el-table-column prop="flowName" label="流程" width="150" />
      <el-table-column prop="businessKey" label="业务主键" width="140" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="currentNodeName" label="当前节点" width="160" />
      <el-table-column prop="startedAt" label="发起时间" width="180">
        <template #default="{ row }">{{ fmtDateTime(row.startedAt) }}</template>
      </el-table-column>
      <el-table-column prop="endedAt" label="结束时间" width="180">
        <template #default="{ row }">{{ fmtDateTime(row.endedAt) }}</template>
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
import { useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import TablePagination from '@/components/TablePagination.vue'
import { submittedInstances } from '@/api/workbench'
import { fmtDateTime } from '@/utils/format'
import type { InstanceView } from '@/api/types'
import { statusText, statusType } from '@/utils/labels'

const router = useRouter()
const instances = ref<InstanceView[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const status = ref('')
const keyword = ref('')
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const result = await submittedInstances({
      page: page.value,
      size: size.value,
      status: status.value || undefined,
      keyword: keyword.value || undefined,
    })
    instances.value = result.list
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function reload() {
  page.value = 1
  load()
}

function goDetail(instanceId: string) {
  router.push({ name: 'instance-detail', params: { instanceId } })
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; align-items: center; justify-content: flex-end; gap: 8px; margin-bottom: 12px; }
</style>
