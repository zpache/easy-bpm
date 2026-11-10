<template>
  <el-card>
    <div class="toolbar">
      <el-tabs v-model="tab" style="flex: 1" @tab-change="onTab">
        <el-tab-pane label="全部抄送" name="all" />
        <el-tab-pane label="未读" name="unread" />
      </el-tabs>
      <el-input v-model="keyword" placeholder="按审批标题搜索" clearable style="width: 240px"
                @keyup.enter="load" @clear="load" />
      <el-button style="margin-left: 8px" @click="load">搜索</el-button>
    </div>

    <el-table :data="items" v-loading="loading">
      <template #empty>
        <el-empty description="暂无抄送" :image-size="80" />
      </template>
      <el-table-column label="审批标题" min-width="240" show-overflow-tooltip>
        <template #default="{ row }">
          <el-badge is-dot :hidden="row.read" style="margin-right: 6px" />
          <el-link type="primary" @click="goDetail(row)">{{ row.instanceTitle }}</el-link>
        </template>
      </el-table-column>
      <el-table-column prop="flowName" label="流程" width="150" />
      <el-table-column prop="nodeName" label="抄送节点" width="150" />
      <el-table-column prop="initiatorName" label="发起人" width="110" />
      <el-table-column label="实例状态" width="110">
        <template #default="{ row }">
          <el-tag v-if="row.instanceStatus" :type="statusType(row.instanceStatus)" size="small" effect="light" round>
            {{ statusText(row.instanceStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="抄送时间" width="180" />
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="goDetail(row)">查看</el-button>
          <el-button v-if="!row.read" link @click="markRead(row)">标记已读</el-button>
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
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ccList, markCcRead } from '@/api/workbench'
import type { CcView } from '@/api/types'
import { statusText, statusType } from '@/utils/labels'

const router = useRouter()
const tab = ref<'all' | 'unread'>('all')
const items = ref<CcView[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const result = await ccList({
      page: page.value,
      size: size.value,
      keyword: keyword.value,
      unreadOnly: tab.value === 'unread',
    })
    items.value = result.list
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function onTab() {
  page.value = 1
  load()
}

async function markRead(row: CcView) {
  await markCcRead(row.id)
  row.read = true
  ElMessage.success('已标记为已读')
  if (tab.value === 'unread') await load()
}

function goDetail(row: CcView) {
  router.push({ name: 'instance-detail', params: { instanceId: row.instanceId } })
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; align-items: center; margin-bottom: 8px; }
</style>
