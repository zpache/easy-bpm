<template>
  <el-card v-if="detail">
    <div class="toolbar">
      <el-button @click="router.back()">返回</el-button>
      <span class="title">{{ detail.instance.title }}</span>
      <el-tag :type="statusType(detail.instance.status)">{{ statusText(detail.instance.status) }}</el-tag>
    </div>

    <el-descriptions :column="3" border>
      <el-descriptions-item label="流程">{{ detail.instance.flowName }}</el-descriptions-item>
      <el-descriptions-item label="发起人">{{ detail.instance.initiatorName }}</el-descriptions-item>
      <el-descriptions-item label="业务主键">{{ detail.instance.businessKey || '-' }}</el-descriptions-item>
      <el-descriptions-item label="当前节点">{{ detail.instance.currentNodeName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="发起时间">{{ fmtDateTime(detail.instance.startedAt) }}</el-descriptions-item>
      <el-descriptions-item label="结束时间">{{ fmtDateTime(detail.instance.endedAt) }}</el-descriptions-item>
    </el-descriptions>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>审批时间线</template>
      <el-timeline>
        <el-timeline-item
          v-for="(item, index) in detail.timeline"
          :key="index"
          :type="timelineType(item.action)"
          :timestamp="fmtDateTime(item.createdAt)"
        >
          <div class="tl-head">
            <el-tag size="small" :type="item.action === 'REJECT' ? 'danger' : item.action === 'APPROVE' ? 'success' : 'primary'">
              {{ actionText(item.action) }}
            </el-tag>
            <span class="tl-operator">{{ item.operatorName }}</span>
            <span v-if="item.nodeName" class="tl-node">{{ item.nodeName }}</span>
          </div>
          <div v-if="item.comment" class="tl-comment">{{ item.comment }}</div>
        </el-timeline-item>
      </el-timeline>
    </el-card>

    <el-card v-if="detail.pendingTasks.length" shadow="never" style="margin-top: 16px">
      <template #header>当前待办</template>
      <el-table :data="detail.pendingTasks" size="default">
        <el-table-column prop="nodeName" label="节点" />
        <el-table-column prop="assigneeName" label="处理人" />
        <el-table-column prop="createdAt" label="到达时间" width="180">
          <template #default="{ row }">{{ fmtDateTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>表单数据</template>
      <el-descriptions v-if="formRows.length" :column="2" border>
        <el-descriptions-item v-for="row in formRows" :key="row.label" :label="row.label">
          {{ row.value }}
        </el-descriptions-item>
      </el-descriptions>
      <el-empty v-else description="无表单数据" :image-size="60" />
      <details v-if="rawJson" class="json-collapse">
        <summary>原始 JSON</summary>
        <pre class="form-data">{{ rawJson }}</pre>
      </details>
    </el-card>
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { instanceDetail } from '@/api/workbench'
import { fmtDateTime } from '@/utils/format'
import type { InstanceDetail } from '@/api/types'
import { actionText, actionType, statusText, statusType } from '@/utils/labels'

const route = useRoute()
const router = useRouter()
const detail = ref<InstanceDetail | null>(null)

function timelineType(action?: string) {
  return actionType(action) === 'danger' ? 'danger' : actionType(action) === 'success' ? 'success' : 'primary'
}

interface FormFieldMeta {
  key?: string
  label?: string
  type?: string
  required?: boolean
}

/** 按 formSchema 字段元数据渲染为「字段名: 值」；无 schema 时按 formData 键值展示 */
const formRows = computed(() => {
  if (!detail.value) return []
  const data = (detail.value.instance.formData ?? {}) as Record<string, unknown>
  const schema = detail.value.formSchema as { fields?: FormFieldMeta[] } | null
  const fields = schema?.fields ?? []
  if (fields.length) {
    return fields
      .map((f) => ({ label: f.label || f.key || '-', value: formatValue(data[f.key ?? '']) }))
      .filter((r) => r.value !== '')
  }
  return Object.entries(data)
    .map(([key, value]) => ({ label: key, value: formatValue(value) }))
    .filter((r) => r.value !== '')
})

const rawJson = computed(() => {
  const data = detail.value?.instance.formData
  return data && Object.keys(data).length ? JSON.stringify(data, null, 2) : ''
})

function formatValue(value: unknown): string {
  if (value === null || value === undefined || value === '') return ''
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

onMounted(async () => {
  detail.value = await instanceDetail(route.params.instanceId as string)
})
</script>

<style scoped>
.toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.title { font-size: 16px; font-weight: 600; }
.tl-head { display: flex; align-items: center; gap: 8px; }
.tl-operator { font-weight: 600; }
.tl-node { color: #909399; font-size: 13px; }
.tl-comment { margin-top: 4px; color: #606266; font-size: 13px; background: #f5f7fa; padding: 6px 10px; border-radius: 4px; }
.json-collapse { margin-top: 12px; }
.json-collapse summary { cursor: pointer; color: #909399; font-size: 13px; }
.form-data { font-size: 12px; line-height: 1.6; color: #606266; white-space: pre-wrap; background: #f5f7fa; border-radius: 6px; padding: 10px 12px; margin-top: 8px; }
</style>
