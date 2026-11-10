<template>
  <div>
    <el-row :gutter="16">
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-label">实例总数</div>
          <div class="stat-value">{{ summary.totalInstances ?? '-' }}</div>
        </el-card>
      </el-col>
      <el-col :span="6" v-for="item in statCards" :key="item.key">
        <el-card shadow="hover">
          <div class="stat-label">{{ item.label }}</div>
          <div class="stat-value" :style="{ color: item.color }">{{ statusStats[item.key] ?? 0 }}</div>
        </el-card>
      </el-col>
    </el-row>
    <el-card style="margin-top: 16px">
      <template #header>最近发起</template>
      <el-table :data="summary.recent || []" size="default">
        <el-table-column prop="title" label="标题" min-width="200" />
        <el-table-column prop="flowName" label="流程" width="160" />
        <el-table-column prop="initiatorName" label="发起人" width="120" />
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
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
import { dashboardSummary } from '@/api/workbench'
import { statusText, statusType } from '@/utils/labels'

interface Summary {
  totalInstances?: number
  statusStats?: Record<string, number>
  recent?: Array<Record<string, unknown>>
}

const summary = ref<Summary>({})
const statusStats = computed(() => summary.value.statusStats ?? {})
const statCards = [
  { key: 'RUNNING', label: '进行中', color: '#409eff' },
  { key: 'APPROVED', label: '已通过', color: '#67c23a' },
  { key: 'REJECTED', label: '已驳回', color: '#f56c6c' },
]

onMounted(async () => {
  summary.value = await dashboardSummary()
})
</script>

<style scoped>
.stat-label { color: #909399; font-size: 13px; }
.stat-value { font-size: 32px; font-weight: 700; margin-top: 8px; }
</style>
