<template>
  <div>
    <el-row :gutter="16">
      <el-col :xs="12" :sm="6" v-for="card in cards" :key="card.key">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-body">
            <div class="stat-icon" :style="{ background: card.bg, color: card.color }">
              <el-icon :size="22"><component :is="card.icon" /></el-icon>
            </div>
            <div class="stat-meta">
              <div class="stat-label">{{ card.label }}</div>
              <div class="stat-value" :style="{ color: card.color }">{{ card.value }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card style="margin-top: 16px">
      <template #header>
        <div class="recent-header">
          <span>最近发起</span>
          <el-link type="primary" :underline="false" @click="router.push('/workbench/submitted')">
            查看全部 <el-icon><ArrowRight /></el-icon>
          </el-link>
        </div>
      </template>
      <el-table :data="summary.recent || []" size="default" v-loading="loading">
        <template #empty>
          <el-empty description="暂无审批实例" :image-size="80" />
        </template>
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="flowName" label="流程" width="160" />
        <el-table-column prop="initiatorName" label="发起人" width="120" />
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small" effect="light" round>{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="currentNodeName" label="当前节点" width="160" />
        <el-table-column prop="startedAt" label="发起时间" width="180" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, CircleCheck, CircleClose, DataLine, Loading } from '@element-plus/icons-vue'
import { dashboardSummary } from '@/api/workbench'
import { statusText, statusType } from '@/utils/labels'

const router = useRouter()
const loading = ref(false)

interface Summary {
  totalInstances?: number
  statusStats?: Record<string, number>
  recent?: Array<Record<string, unknown>>
}

const summary = ref<Summary>({})
const statusStats = computed(() => summary.value.statusStats ?? {})

const cards = computed(() => [
  { key: 'total', label: '实例总数', value: summary.value.totalInstances ?? '-', color: '#0960bd', bg: '#eff4fb', icon: DataLine },
  { key: 'RUNNING', label: '进行中', value: statusStats.value.RUNNING ?? 0, color: '#0960bd', bg: '#eff4fb', icon: Loading },
  { key: 'APPROVED', label: '已通过', value: statusStats.value.APPROVED ?? 0, color: '#3eb575', bg: '#e8f7ef', icon: CircleCheck },
  { key: 'REJECTED', label: '已驳回', value: statusStats.value.REJECTED ?? 0, color: '#e55454', bg: '#fdecec', icon: CircleClose },
])

onMounted(async () => {
  loading.value = true
  try {
    summary.value = await dashboardSummary()
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.stat-card :deep(.el-card__body) { padding: 20px; }
.stat-body { display: flex; align-items: center; gap: 14px; }
.stat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 12px;
  flex-shrink: 0;
}
.stat-label { color: #8a919f; font-size: 13px; }
.stat-value { font-size: 26px; font-weight: 700; margin-top: 4px; line-height: 1; }
.recent-header { display: flex; align-items: center; justify-content: space-between; }
.recent-header .el-link { font-size: 13px; }
</style>
