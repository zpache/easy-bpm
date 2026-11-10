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
      <el-descriptions-item label="发起时间">{{ detail.instance.startedAt }}</el-descriptions-item>
      <el-descriptions-item label="结束时间">{{ detail.instance.endedAt || '-' }}</el-descriptions-item>
    </el-descriptions>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>审批时间线</template>
      <el-timeline>
        <el-timeline-item
          v-for="(item, index) in detail.timeline"
          :key="index"
          :type="timelineType(item.action)"
          :timestamp="item.createdAt"
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
        <el-table-column prop="createTime" label="到达时间" width="180" />
      </el-table>
    </el-card>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>表单数据</template>
      <pre class="form-data">{{ JSON.stringify(detail.instance.formData ?? {}, null, 2) }}</pre>
    </el-card>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { instanceDetail } from '@/api/workbench'
import type { InstanceDetail } from '@/api/types'
import { actionText, actionType, statusText, statusType } from '@/utils/labels'

const route = useRoute()
const router = useRouter()
const detail = ref<InstanceDetail | null>(null)

function timelineType(action?: string) {
  return actionType(action) === 'danger' ? 'danger' : actionType(action) === 'success' ? 'success' : 'primary'
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
.form-data { font-size: 12px; line-height: 1.6; color: #606266; white-space: pre-wrap; }
</style>
