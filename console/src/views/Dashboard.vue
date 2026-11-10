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

    <!-- 管理员数据看板 -->
    <el-row v-if="auth.isTenantAdmin" :gutter="16" style="margin-top: 16px">
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-body">
            <div class="stat-icon" style="background: #e8f7ef; color: #3eb575">
              <el-icon :size="22"><TrendCharts /></el-icon>
            </div>
            <div class="stat-meta">
              <div class="stat-label">通过率</div>
              <div class="stat-value" style="color: #3eb575">{{ stats ? `${stats.approvalRate}%` : '-' }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-body">
            <div class="stat-icon" style="background: #f0f3fb; color: #6a5acd">
              <el-icon :size="22"><Timer /></el-icon>
            </div>
            <div class="stat-meta">
              <div class="stat-label">平均审批时长</div>
              <div class="stat-value" style="color: #6a5acd">{{ stats ? `${stats.avgApprovalHours}h` : '-' }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row v-if="auth.isTenantAdmin" :gutter="16" style="margin-top: 16px">
      <el-col :xs="24" :sm="14">
        <el-card>
          <template #header>近 14 天审批趋势</template>
          <div v-if="stats" class="trend-chart" v-loading="loading">
            <div class="trend-bars">
              <div v-for="point in stats.trend" :key="point.day" class="trend-col">
                <div class="trend-stack">
                  <div class="bar finished" :style="{ height: barHeight(point.finished) }" />
                  <div class="bar started" :style="{ height: barHeight(point.started) }" />
                </div>
                <div class="trend-label">{{ point.day.slice(5) }}</div>
                <div class="trend-tip">{{ point.day }}：发起 {{ point.started }} / 办结 {{ point.finished }}</div>
              </div>
            </div>
            <div class="trend-legend">
              <span class="legend-item"><i class="dot dot-started" />发起</span>
              <span class="legend-item"><i class="dot dot-finished" />办结</span>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="10">
        <el-card>
          <template #header>流程发起 TOP5</template>
          <div v-if="stats && stats.topFlows.length" class="top-flows">
            <div v-for="flow in stats.topFlows" :key="flow.flowCode" class="top-flow-row">
              <span class="top-flow-name">{{ flow.flowName }}</span>
              <div class="top-flow-bar-wrap">
                <div class="top-flow-bar" :style="{ width: topFlowWidth(flow.count) }" />
              </div>
              <span class="top-flow-count">{{ flow.count }}</span>
            </div>
          </div>
          <el-empty v-else description="暂无数据" :image-size="60" />
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
import { ArrowRight, CircleCheck, CircleClose, DataLine, Loading, Timer, TrendCharts } from '@element-plus/icons-vue'
import { dashboardStats, dashboardSummary, type DashboardStats } from '@/api/workbench'
import { useAuthStore } from '@/stores/auth'
import { statusText, statusType } from '@/utils/labels'

const auth = useAuthStore()
const router = useRouter()
const loading = ref(false)

interface Summary {
  totalInstances?: number
  statusStats?: Record<string, number>
  recent?: Array<Record<string, unknown>>
}

const summary = ref<Summary>({})
const stats = ref<DashboardStats | null>(null)
const statusStats = computed(() => summary.value.statusStats ?? {})

const cards = computed(() => [
  { key: 'total', label: '实例总数', value: summary.value.totalInstances ?? '-', color: '#0960bd', bg: '#eff4fb', icon: DataLine },
  { key: 'RUNNING', label: '进行中', value: statusStats.value.RUNNING ?? 0, color: '#0960bd', bg: '#eff4fb', icon: Loading },
  { key: 'APPROVED', label: '已通过', value: statusStats.value.APPROVED ?? 0, color: '#3eb575', bg: '#e8f7ef', icon: CircleCheck },
  { key: 'REJECTED', label: '已驳回', value: statusStats.value.REJECTED ?? 0, color: '#e55454', bg: '#fdecec', icon: CircleClose },
])

const maxTrend = computed(() =>
  Math.max(1, ...(stats.value?.trend ?? []).map((p) => Math.max(p.started, p.finished))),
)

function barHeight(value: number) {
  return `${Math.round((value / maxTrend.value) * 100)}%`
}

const maxTopFlow = computed(() => Math.max(1, ...(stats.value?.topFlows ?? []).map((f) => f.count)))

function topFlowWidth(count: number) {
  return `${Math.round((count / maxTopFlow.value) * 100)}%`
}

onMounted(async () => {
  loading.value = true
  try {
    const requests: Promise<unknown>[] = [dashboardSummary().then((data) => (summary.value = data))]
    if (auth.isTenantAdmin) {
      requests.push(dashboardStats().then((data) => (stats.value = data)))
    }
    await Promise.allSettled(requests)
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

/* 趋势图（纯 CSS 柱状） */
.trend-chart { padding: 4px 0; }
.trend-bars {
  display: flex;
  align-items: flex-end;
  gap: 6px;
  height: 180px;
}
.trend-col { position: relative; flex: 1; height: 100%; display: flex; flex-direction: column; justify-content: flex-end; }
.trend-stack { flex: 1; display: flex; align-items: flex-end; justify-content: center; gap: 3px; }
.bar { width: 38%; max-width: 26px; min-height: 2px; border-radius: 3px 3px 0 0; transition: height 0.4s ease; }
.bar.started { background: #0960bd; }
.bar.finished { background: #3eb575; }
.trend-label { text-align: center; font-size: 10px; color: #8a919f; margin-top: 6px; white-space: nowrap; }
.trend-tip {
  position: absolute;
  bottom: 100%;
  left: 50%;
  transform: translateX(-50%);
  background: #303133;
  color: #fff;
  font-size: 11px;
  padding: 4px 8px;
  border-radius: 4px;
  white-space: nowrap;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.15s;
  z-index: 1;
}
.trend-col:hover .trend-tip { opacity: 1; }
.trend-legend { display: flex; gap: 16px; margin-top: 10px; justify-content: center; }
.legend-item { display: inline-flex; align-items: center; gap: 5px; font-size: 12px; color: #606266; }
.dot { width: 10px; height: 10px; border-radius: 2px; display: inline-block; }
.dot-started { background: #0960bd; }
.dot-finished { background: #3eb575; }

/* TOP 流程 */
.top-flows { display: flex; flex-direction: column; gap: 14px; padding: 4px 0; }
.top-flow-row { display: flex; align-items: center; gap: 10px; }
.top-flow-name { width: 110px; font-size: 13px; color: #303133; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.top-flow-bar-wrap { flex: 1; height: 14px; background: #f0f2f5; border-radius: 7px; overflow: hidden; }
.top-flow-bar { height: 100%; background: linear-gradient(90deg, #0960bd, #3aa0ff); border-radius: 7px; transition: width 0.4s ease; }
.top-flow-count { width: 44px; text-align: right; font-size: 13px; font-weight: 600; color: #303133; }
</style>
