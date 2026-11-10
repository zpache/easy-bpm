<template>
  <div>
    <el-card>
      <template #header>租户信息</template>
      <el-descriptions :column="3" border v-loading="infoLoading">
        <el-descriptions-item label="租户编码">{{ info?.code || '-' }}</el-descriptions-item>
        <el-descriptions-item label="租户名称">{{ info?.name || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="info?.status === 'ACTIVE' ? 'success' : 'danger'" size="small" effect="light" round>
            {{ info?.status === 'ACTIVE' ? '正常' : '已停用' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ info?.remark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ info?.createdAt || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card style="margin-top: 16px">
      <template #header>
        <div class="cred-header">
          <span>接入凭证（AppKey / AppSecret）</span>
          <el-button type="primary" @click="openCreate">新建凭证</el-button>
        </div>
      </template>
      <el-alert type="info" :closable="false" style="margin-bottom: 12px"
        title="AppSecret 仅在新建和轮换时完整展示一次，请立即保存到接入方配置；泄露后请立即轮换。" />
      <el-table :data="credentials" v-loading="loading">
        <template #empty><el-empty description="暂无凭证" :image-size="80" /></template>
        <el-table-column prop="name" label="名称" min-width="140" />
        <el-table-column prop="appKey" label="AppKey" width="220" />
        <el-table-column prop="appSecretMasked" label="AppSecret" width="180" />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small" effect="light" round>
              {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastUsedAt" label="最近使用" width="180">
          <template #default="{ row }">{{ row.lastUsedAt ? fmtDateTime(row.lastUsedAt) : '从未使用' }}</template>
        </el-table-column>
        <el-table-column prop="expireAt" label="过期时间" width="180">
          <template #default="{ row }">{{ row.expireAt ? fmtDateTime(row.expireAt) : '不过期' }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180">
          <template #default="{ row }">{{ fmtDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-popconfirm title="轮换后旧 AppSecret 立即失效，接入方需同步更新，确认？" width="260" @confirm="onRotate(row)">
              <template #reference><el-button size="small" type="warning" plain>轮换密钥</el-button></template>
            </el-popconfirm>
            <el-popconfirm v-if="row.status === 'ACTIVE'" title="停用后该凭证无法调用 OpenAPI，确认？" width="240" @confirm="onDisable(row)">
              <template #reference><el-button size="small" type="danger" plain>停用</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新建凭证 -->
    <el-dialog v-model="createVisible" title="新建凭证" width="460px">
      <el-form :model="createForm" label-width="90px">
        <el-form-item label="凭证名称" required>
          <el-input v-model="createForm.name" placeholder="如：ERP 生产环境" maxlength="50" />
        </el-form-item>
        <el-form-item label="过期时间">
          <el-date-picker v-model="createForm.expireAt" type="datetime" placeholder="留空表示不过期" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 密钥一次性展示 -->
    <el-dialog v-model="secretVisible" :title="secretTitle" width="520px" :close-on-click-modal="false">
      <el-alert type="warning" :closable="false" style="margin-bottom: 16px"
        title="请立即复制保存，关闭后将无法再次查看完整 AppSecret。" />
      <el-form label-width="90px">
        <el-form-item label="凭证名称">{{ secretData?.name }}</el-form-item>
        <el-form-item label="AppKey">
          <div class="secret-row">
            <code class="secret-code">{{ secretData?.appKey }}</code>
            <el-button size="small" @click="copy(secretData?.appKey)">复制</el-button>
          </div>
        </el-form-item>
        <el-form-item label="AppSecret">
          <div class="secret-row">
            <code class="secret-code">{{ secretData?.appSecret }}</code>
            <el-button size="small" type="primary" @click="copy(secretData?.appSecret)">复制</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="secretVisible = false">我已保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createCredential, disableCredential, listCredentials, rotateCredential, tenantInfo } from '@/api/tenant'
import { fmtDateTime } from '@/utils/format'
import type { CredentialSecretView, TenantCredentialView, TenantInfo } from '@/api/types'

const info = ref<TenantInfo | null>(null)
const credentials = ref<TenantCredentialView[]>([])
const infoLoading = ref(false)
const loading = ref(false)
const createVisible = ref(false)
const saving = ref(false)
const secretVisible = ref(false)
const secretTitle = ref('')
const secretData = ref<CredentialSecretView | null>(null)
const createForm = reactive({ name: '', expireAt: '' })

async function load() {
  loading.value = true
  try {
    credentials.value = await listCredentials()
  } finally {
    loading.value = false
  }
}

async function loadInfo() {
  infoLoading.value = true
  try {
    info.value = await tenantInfo()
  } finally {
    infoLoading.value = false
  }
}

function openCreate() {
  createForm.name = ''
  createForm.expireAt = ''
  createVisible.value = true
}

async function onCreate() {
  if (!createForm.name.trim()) {
    ElMessage.warning('请填写凭证名称')
    return
  }
  saving.value = true
  try {
    const created = await createCredential({
      name: createForm.name.trim(),
      expireAt: createForm.expireAt || undefined,
    })
    createVisible.value = false
    secretTitle.value = '凭证创建成功'
    secretData.value = created
    secretVisible.value = true
    await load()
  } finally {
    saving.value = false
  }
}

async function onRotate(row: TenantCredentialView) {
  const rotated = await rotateCredential(row.id)
  secretTitle.value = '密钥轮换成功'
  secretData.value = rotated
  secretVisible.value = true
  await load()
}

async function onDisable(row: TenantCredentialView) {
  await disableCredential(row.id)
  ElMessage.success('已停用')
  await load()
}

async function copy(text?: string) {
  if (!text) return
  await navigator.clipboard.writeText(text)
  ElMessage.success('已复制')
}

onMounted(() => {
  loadInfo()
  load()
})
</script>

<style scoped>
.cred-header { display: flex; align-items: center; justify-content: space-between; }
.secret-row { display: flex; align-items: center; gap: 8px; width: 100%; }
.secret-code {
  flex: 1;
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 6px 10px;
  font-size: 13px;
  word-break: break-all;
  font-family: 'SFMono-Regular', Consolas, Menlo, monospace;
}
</style>
