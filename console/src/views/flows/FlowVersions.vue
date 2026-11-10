<template>
  <el-card>
    <div class="toolbar">
      <el-button @click="router.back()">返回</el-button>
      <span class="flow-title">{{ code }} · 版本历史</span>
    </div>
    <el-table :data="versions">
      <el-table-column prop="version" label="版本" width="80">
        <template #default="{ row }">
          <el-tag v-if="row.version === currentVersion" type="success" size="small">v{{ row.version }} 当前</el-tag>
          <span v-else>v{{ row.version }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="changelog" label="发布说明" min-width="240" />
      <el-table-column prop="publishedBy" label="发布人" width="120" />
      <el-table-column prop="publishedAt" label="发布时间" width="180">
        <template #default="{ row }">{{ fmtDateTime(row.publishedAt) }}</template>
      </el-table-column>
      <el-table-column prop="processDefinitionId" label="引擎定义ID" width="320" show-overflow-tooltip />
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-popconfirm title="回滚到该版本并发布为新版本？" @confirm="onRollback(row.version)">
            <template #reference>
              <el-button size="small" :disabled="row.version === currentVersion || !auth.isTenantAdmin">回滚</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchFlow, listVersions, rollbackFlow } from '@/api/flows'
import { fmtDateTime } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const code = computed(() => route.params.code as string)
const versions = ref<Awaited<ReturnType<typeof listVersions>>>([])
const currentVersion = ref(0)

async function load() {
  const [definition, list] = await Promise.all([fetchFlow(code.value), listVersions(code.value)])
  currentVersion.value = definition.version
  versions.value = list
}

async function onRollback(version: number) {
  await rollbackFlow(code.value, version)
  ElMessage.success(`已回滚并发布为新版本`)
  await load()
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.flow-title { font-weight: 600; }
</style>
