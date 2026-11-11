<template>
  <div>
    <el-alert v-if="auth.switchedTenant" type="warning" :closable="false" style="margin-bottom: 16px">
      <div class="switch-alert">
        <span>当前正以平台管理员身份管理租户「{{ auth.switchedTenant.name }}」，看到的均为该租户数据。</span>
        <el-button size="small" type="warning" @click="onExitTenant">返回平台管理</el-button>
      </div>
    </el-alert>

    <el-card>
      <template #header>
        <div class="toolbar">
          <span>租户管理</span>
          <div class="toolbar-right">
            <el-input
              v-model="keyword"
              placeholder="搜索编码 / 名称 / 备注"
              clearable
              style="width: 240px; margin-right: 12px"
              :prefix-icon="Search"
              @keyup.enter="reload"
              @clear="reload"
            />
            <el-button @click="reload">搜索</el-button>
            <el-button type="primary" @click="openCreate">新建租户</el-button>
          </div>
        </div>
      </template>

      <el-table :data="tenants" v-loading="loading">
        <template #empty><el-empty description="暂无租户" :image-size="80" /></template>
        <el-table-column prop="code" label="租户编码" width="160">
          <template #default="{ row }">
            <span class="code-cell">{{ row.code }}</span>
            <el-tag v-if="row.code === 'platform'" size="small" type="warning" effect="light" round style="margin-left: 6px">平台</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="租户名称" min-width="140" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'" size="small" effect="light" round>
              {{ row.status === 'ACTIVE' ? '正常' : '已停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="consoleUserCount" label="控制台账号" width="100" align="center" />
        <el-table-column prop="credentialCount" label="接入凭证" width="90" align="center" />
        <el-table-column prop="publishedFlowCount" label="已发布流程" width="100" align="center" />
        <el-table-column prop="runningInstanceCount" label="运行中实例" width="100" align="center" />
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="创建时间" width="170">
          <template #default="{ row }">{{ (row.createdAt || '').replace('T', ' ').slice(0, 19) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" plain :disabled="row.status !== 'ACTIVE'" @click="onSwitch(row)">
              进入租户
            </el-button>
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-popconfirm
              v-if="row.status === 'ACTIVE' && row.code !== 'platform'"
              title="停用后该租户控制台登录与 OpenAPI 全部拒绝，确认？"
              width="260"
              @confirm="onDisable(row)"
            >
              <template #reference><el-button size="small" type="danger" plain>停用</el-button></template>
            </el-popconfirm>
            <el-button v-else-if="row.status === 'DISABLED'" size="small" type="success" plain @click="onEnable(row)">
              启用
            </el-button>
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

    <!-- 新建租户 -->
    <el-dialog v-model="createVisible" title="新建租户" width="520px" :close-on-click-modal="false">
      <el-form :model="createForm" label-width="110px">
        <el-form-item label="租户编码" required>
          <el-input v-model="createForm.code" placeholder="小写字母开头，2-32 位（如 acme）" maxlength="32" />
        </el-form-item>
        <el-form-item label="租户名称" required>
          <el-input v-model="createForm.name" placeholder="如：阿科玛科技" maxlength="128" />
        </el-form-item>
        <el-form-item label="初始管理员">
          <el-input v-model="createForm.adminUsername" placeholder="控制台登录账号（可留空）" maxlength="64" />
        </el-form-item>
        <el-form-item label="管理员密码">
          <el-input
            v-model="createForm.adminPassword"
            type="password"
            show-password
            placeholder="至少 6 位，填写账号时必填"
            maxlength="64"
          />
        </el-form-item>
        <el-form-item label="管理员姓名">
          <el-input v-model="createForm.adminDisplayName" placeholder="留空默认为「租户名称+管理员」" maxlength="64" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" type="textarea" :rows="2" maxlength="500" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 编辑租户 -->
    <el-dialog v-model="editVisible" title="编辑租户" width="480px" :close-on-click-modal="false">
      <el-form :model="editForm" label-width="90px">
        <el-form-item label="租户编码">
          <el-input :model-value="editing?.code" disabled />
        </el-form-item>
        <el-form-item label="租户名称" required>
          <el-input v-model="editForm.name" maxlength="128" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="editForm.remark" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onUpdate">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { createTenant, disableTenant, enableTenant, listTenants, switchTenant, updateTenant } from '@/api/platform'
import type { PlatformTenantView } from '@/api/types'

const auth = useAuthStore()
const router = useRouter()

const tenants = ref<PlatformTenantView[]>([])
const loading = ref(false)
const saving = ref(false)
const keyword = ref('')
const page = ref(1)
const size = ref(20)
const total = ref(0)
const createVisible = ref(false)
const editVisible = ref(false)
const editing = ref<PlatformTenantView | null>(null)
const createForm = reactive({ code: '', name: '', adminUsername: '', adminPassword: '', adminDisplayName: '', remark: '' })
const editForm = reactive({ name: '', remark: '' })

let loadSequence = 0

async function load() {
  const sequence = ++loadSequence
  loading.value = true
  try {
    const result = await listTenants({
      page: page.value,
      size: size.value,
      keyword: keyword.value.trim() || undefined,
    })
    if (sequence !== loadSequence) return
    const lastPage = Math.max(1, Math.ceil(result.total / size.value))
    if (page.value > lastPage) {
      page.value = lastPage
      await load()
      return
    }
    tenants.value = result.list
    total.value = result.total
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}

async function reload() {
  page.value = 1
  await load()
}

function openCreate() {
  createForm.code = ''
  createForm.name = ''
  createForm.adminUsername = ''
  createForm.adminPassword = ''
  createForm.adminDisplayName = ''
  createForm.remark = ''
  createVisible.value = true
}

async function onCreate() {
  if (!createForm.code.trim() || !createForm.name.trim()) {
    ElMessage.warning('请填写租户编码和名称')
    return
  }
  if (createForm.adminUsername.trim() && createForm.adminPassword.length < 6) {
    ElMessage.warning('填写了初始管理员账号时，密码至少 6 位')
    return
  }
  saving.value = true
  try {
    await createTenant({
      code: createForm.code.trim(),
      name: createForm.name.trim(),
      remark: createForm.remark.trim() || undefined,
      adminUsername: createForm.adminUsername.trim() || undefined,
      adminPassword: createForm.adminPassword || undefined,
      adminDisplayName: createForm.adminDisplayName.trim() || undefined,
    })
    createVisible.value = false
    ElMessage.success('租户创建成功')
    await reload()
  } finally {
    saving.value = false
  }
}

function openEdit(row: PlatformTenantView) {
  editing.value = row
  editForm.name = row.name
  editForm.remark = row.remark || ''
  editVisible.value = true
}

async function onUpdate() {
  if (!editing.value) return
  if (!editForm.name.trim()) {
    ElMessage.warning('请填写租户名称')
    return
  }
  saving.value = true
  try {
    await updateTenant(editing.value.id, { name: editForm.name.trim(), remark: editForm.remark })
    editVisible.value = false
    ElMessage.success('已保存')
    await load()
  } finally {
    saving.value = false
  }
}

async function onDisable(row: PlatformTenantView) {
  await disableTenant(row.id)
  ElMessage.success(`租户「${row.name}」已停用`)
  await load()
}

async function onEnable(row: PlatformTenantView) {
  await enableTenant(row.id)
  ElMessage.success(`租户「${row.name}」已启用`)
  await load()
}

async function onSwitch(row: PlatformTenantView) {
  await ElMessageBox.confirm(
    `将切换到租户「${row.name}」的管理视角，平台会话已保存，可随时返回。`,
    '进入租户',
    { confirmButtonText: '进入', cancelButtonText: '取消', type: 'info' },
  )
  const result = await switchTenant(row.id)
  auth.enterTenant(result)
  ElMessage.success(`已进入租户「${row.name}」`)
  router.push('/dashboard')
}

async function onExitTenant() {
  auth.exitTenant()
  ElMessage.success('已返回平台管理')
  router.push('/platform')
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; align-items: center; justify-content: space-between; }
.toolbar-right { display: flex; align-items: center; }
.code-cell { font-family: 'SFMono-Regular', Consolas, Menlo, monospace; }
.switch-alert { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.pager { display: flex; justify-content: flex-end; margin-top: 12px; }
</style>
