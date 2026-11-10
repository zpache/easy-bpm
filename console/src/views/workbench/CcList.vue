<template>
  <el-card>
    <div class="toolbar">
      <el-tabs v-model="tab" class="toolbar-tabs" @tab-change="onTab">
        <el-tab-pane label="全部抄送" name="all" />
        <el-tab-pane name="unread">
          <template #label>
            未读<span v-if="unreadCount > 0" class="tab-count">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
          </template>
        </el-tab-pane>
      </el-tabs>
      <div class="toolbar-right">
        <el-input
          v-model="keyword"
          placeholder="按审批标题搜索"
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

    <el-table :data="items" v-loading="loading" stripe :row-class-name="rowClass" @row-click="goDetail">
      <template #empty>
        <el-empty :description="tab === 'unread' ? '没有未读抄送' : '暂无抄送'" :image-size="80" />
      </template>
      <el-table-column label="审批标题" min-width="240" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="title-cell" :class="{ unread: !row.read }">
            <i v-if="!row.read" class="unread-dot" />
            {{ row.instanceTitle }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="flowName" label="流程" width="150" show-overflow-tooltip />
      <el-table-column prop="nodeName" label="抄送节点" width="140" show-overflow-tooltip />
      <el-table-column prop="initiatorName" label="发起人" width="100" />
      <el-table-column label="实例状态" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.instanceStatus" :type="statusType(row.instanceStatus)" size="small" effect="light" round>
            {{ statusText(row.instanceStatus) }}
          </el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="抄送时间" width="165">
        <template #default="{ row }">{{ fmtDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click.stop="goDetail(row)">查看</el-button>
          <el-button v-if="!row.read" link type="warning" @click.stop="markRead(row)">标记已读</el-button>
        </template>
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
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { ccList, markCcRead, workbenchCounts } from '@/api/workbench'
import type { CcView } from '@/api/types'
import { statusText, statusType } from '@/utils/labels'
import { fmtDateTime } from '@/utils/format'

const router = useRouter()
const tab = ref<'all' | 'unread'>('all')
const items = ref<CcView[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const loading = ref(false)
const unreadCount = ref(0)

const isUnreadTab = computed(() => tab.value === 'unread')

function rowClass({ row }: { row: CcView }) {
  return row.read ? '' : 'cc-unread-row'
}

async function load() {
  loading.value = true
  try {
    const result = await ccList({
      page: page.value,
      size: size.value,
      keyword: keyword.value,
      unreadOnly: isUnreadTab.value,
    })
    items.value = result.list
    total.value = result.total
  } finally {
    loading.value = false
  }
}

async function loadUnreadCount() {
  try {
    unreadCount.value = (await workbenchCounts()).ccUnread
  } catch {
    /* 角标尽力而为 */
  }
}

function onTab() {
  reload()
}

function reload() {
  page.value = 1
  load()
  loadUnreadCount()
}

async function markRead(row: CcView) {
  await markCcRead(row.id)
  row.read = true
  unreadCount.value = Math.max(0, unreadCount.value - 1)
  ElMessage.success('已标记为已读')
  if (isUnreadTab.value) await load()
}

function goDetail(row: CcView) {
  router.push({ name: 'instance-detail', params: { instanceId: row.instanceId } })
}

onMounted(() => {
  load()
  loadUnreadCount()
})
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
.tab-count {
  display: inline-block;
  margin-left: 4px;
  padding: 0 6px;
  min-width: 16px;
  height: 16px;
  line-height: 16px;
  border-radius: 8px;
  background: var(--el-color-danger);
  color: #fff;
  font-size: 11px;
  text-align: center;
}
.title-cell { display: inline-flex; align-items: center; gap: 6px; }
.title-cell.unread { font-weight: 600; color: #303133; }
.unread-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--el-color-danger);
  flex-shrink: 0;
}
.pager { display: flex; justify-content: flex-end; margin-top: 14px; }
:deep(.el-table__row) { cursor: pointer; }
:deep(.cc-unread-row) { background: #fdf6ec; }
:deep(.el-table__row.cc-unread-row:hover > td) { background: #faecd8 !important; }
</style>
