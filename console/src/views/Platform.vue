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
              style="width: 240px"
              :prefix-icon="Search"
              @keyup.enter="reload"
              @clear="reload"
            />
            <el-button type="primary" @click="reload">查询</el-button>
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
        <el-table-column label="操作" width="305" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" plain :disabled="row.status !== 'ACTIVE'" @click="onSwitch(row)">
              进入租户
            </el-button>
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" :disabled="row.code === 'platform'" @click="openSso(row)">SSO</el-button>
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

      <TablePagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :loading="loading"
        @change="load"
      />
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

    <!-- 租户 SSO 配置与身份绑定 -->
    <el-dialog
      v-model="ssoVisible"
      :title="`单点登录 · ${ssoTenant?.name || ''}`"
      width="760px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-tabs v-model="ssoTab" @tab-change="onSsoTabChange">
        <el-tab-pane label="IdP 配置" name="config">
          <el-alert
            type="info"
            :closable="false"
            style="margin-bottom: 16px"
            title="仅支持 OIDC（授权码 + PKCE）。回调地址必须与 IdP 侧登记的 redirect_uri 完全一致。"
          />
          <el-form :model="ssoForm" label-width="110px">
            <el-form-item label="启用登录">
              <el-switch v-model="ssoEnabled" active-text="启用" inactive-text="停用" />
            </el-form-item>
            <el-form-item label="登录按钮名">
              <el-input v-model="ssoForm.name" maxlength="64" placeholder="如：企业微信 / Azure AD" />
            </el-form-item>
            <el-form-item label="Issuer" required>
              <el-input v-model="ssoForm.issuer" placeholder="https://idp.example.com/realms/acme" />
            </el-form-item>
            <el-form-item label="Client ID" required>
              <el-input v-model="ssoForm.clientId" maxlength="191" />
            </el-form-item>
            <el-form-item label="Client Secret">
              <el-input
                v-model="ssoForm.clientSecret"
                type="password"
                show-password
                :placeholder="ssoConfigured ? '已保存，留空表示不修改' : '新建时必填'"
              />
            </el-form-item>
            <el-form-item label="Scopes">
              <el-input v-model="ssoForm.scopes" placeholder="openid profile email" />
            </el-form-item>
            <el-form-item label="回调地址">
              <el-input v-model="ssoForm.redirectUri" placeholder="留空使用系统默认回调地址" />
            </el-form-item>
            <el-form-item label="姓名 claim">
              <el-input v-model="ssoForm.nameClaim" placeholder="留空按 name / nickname / preferred_username 兜底" />
            </el-form-item>
            <el-form-item label="邮箱 claim">
              <el-input v-model="ssoForm.emailClaim" placeholder="留空按 email / mail 兜底" />
            </el-form-item>
          </el-form>

          <el-alert
            v-if="ssoTest"
            :type="ssoTest.ok ? 'success' : 'error'"
            :title="ssoTest.message"
            :closable="false"
            style="margin-bottom: 12px"
          >
            <div v-if="ssoTest.ok" class="test-detail">
              <div>authorization：{{ ssoTest.authorizationEndpoint }}</div>
              <div>token：{{ ssoTest.tokenEndpoint }}</div>
              <div>jwks：{{ ssoTest.jwksUri }}</div>
            </div>
          </el-alert>

          <div class="dialog-footer-inline">
            <el-button :loading="ssoTesting" @click="onTestSso">测试连接</el-button>
            <el-button type="primary" :loading="ssoSaving" @click="onSaveSso">保存配置</el-button>
          </div>
        </el-tab-pane>

        <el-tab-pane label="企业身份" name="identities">
          <div class="identity-toolbar">
            <el-radio-group v-model="identityFilter" @change="loadIdentities">
              <el-radio-button value="">全部</el-radio-button>
              <el-radio-button value="PENDING">待绑定</el-radio-button>
              <el-radio-button value="ACTIVE">已绑定</el-radio-button>
            </el-radio-group>
            <el-button size="small" @click="loadIdentities">刷新</el-button>
          </div>
          <el-table :data="identities" size="small" max-height="380">
            <template #empty>
              <el-empty description="暂无企业身份记录" :image-size="70" />
            </template>
            <el-table-column prop="displayName" label="姓名" width="110" show-overflow-tooltip />
            <el-table-column prop="email" label="邮箱 / 账号" min-width="150" show-overflow-tooltip />
            <el-table-column label="Subject" width="140">
              <template #default="{ row }">
                <span class="code-cell">{{ row.subjectShort }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="identityTagType(row.status)" size="small" effect="light" round>
                  {{ identityStatusText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="绑定账号" width="130">
              <template #default="{ row }">
                <span v-if="row.username">{{ row.username }}<span v-if="row.consoleDisplayName"> · {{ row.consoleDisplayName }}</span></span>
                <span v-else class="muted">未绑定</span>
              </template>
            </el-table-column>
            <el-table-column label="最近尝试" width="160">
              <template #default="{ row }">{{ fmtTime(row.lastAttemptAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status !== 'ACTIVE'" size="small" type="primary" plain @click="openBind(row)">
                  绑定
                </el-button>
                <el-popconfirm
                  v-else
                  title="解绑后该员工将无法再用企业身份登录，确认？"
                  width="250"
                  @confirm="onUnbind(row)"
                >
                  <template #reference><el-button size="small" type="danger" plain>解绑</el-button></template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
          <p class="identity-hint">
            员工首次点击「企业登录」并通过 IdP 认证后，会自动出现在「待绑定」列表；选择本地账号绑定后方可登录。
          </p>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>

    <!-- 绑定本地账号 -->
    <el-dialog v-model="bindVisible" title="绑定控制台账号" width="460px" :close-on-click-modal="false">
      <el-form label-width="90px">
        <el-form-item label="企业身份">
          <div class="bind-identity">
            <div>{{ binding?.displayName || '未提供姓名' }}<span class="muted"> · {{ binding?.email || '无邮箱' }}</span></div>
            <div class="code-cell muted">{{ binding?.subjectShort }}</div>
          </div>
        </el-form-item>
        <el-form-item label="本地账号" required>
          <el-select v-model="bindForm.username" filterable placeholder="选择该租户下的控制台账号" style="width: 100%">
            <el-option
              v-for="candidate in candidates"
              :key="candidate.id"
              :label="`${candidate.username}（${candidate.displayName}）`"
              :value="candidate.username"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="登录方式">
          <el-checkbox v-model="bindForm.ssoOnly">同时清空本地密码，仅允许企业登录</el-checkbox>
        </el-form-item>
      </el-form>
      <el-alert
        v-if="bindForm.ssoOnly"
        type="warning"
        :closable="false"
        title="清空密码后该账号只能走企业登录；若 IdP 故障将无法登录，请确认还有其他可用管理员。"
      />
      <template #footer>
        <el-button @click="bindVisible = false">取消</el-button>
        <el-button type="primary" :loading="bindingSaving" @click="onBind">确认绑定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import TablePagination from '@/components/TablePagination.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { createTenant, disableTenant, enableTenant, listTenants, switchTenant, updateTenant } from '@/api/platform'
import {
  bindSsoIdentity,
  getSsoConfig,
  listSsoCandidates,
  listSsoIdentities,
  saveSsoConfig,
  testSsoConnection,
  unbindSsoIdentity,
} from '@/api/sso'
import type { SsoCandidate, SsoIdentity, SsoTestResult } from '@/api/sso'
import { fmtDateTime } from '@/utils/format'
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

// ---- 租户 SSO 配置 ----
const ssoVisible = ref(false)
const ssoTab = ref('config')
const ssoTenant = ref<PlatformTenantView | null>(null)
const ssoConfigured = ref(false)
const ssoEnabled = ref(true)
const ssoSaving = ref(false)
const ssoTesting = ref(false)
const ssoTest = ref<SsoTestResult | null>(null)
const ssoForm = reactive({
  name: '企业登录',
  issuer: '',
  clientId: '',
  clientSecret: '',
  scopes: 'openid profile email',
  redirectUri: '',
  nameClaim: '',
  emailClaim: '',
})

// ---- 企业身份绑定 ----
const identities = ref<SsoIdentity[]>([])
const identityFilter = ref('')
const bindVisible = ref(false)
const binding = ref<SsoIdentity | null>(null)
const candidates = ref<SsoCandidate[]>([])
const bindForm = reactive({ username: '', ssoOnly: false })
const bindingSaving = ref(false)

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

// ---- SSO 配置 ----
async function openSso(row: PlatformTenantView) {
  ssoTenant.value = row
  ssoVisible.value = true
  ssoTab.value = 'config'
  ssoTest.value = null
  identities.value = []
  identityFilter.value = ''
  await loadSsoConfig(row.id)
}

async function loadSsoConfig(tenantId: string) {
  const config = await getSsoConfig(tenantId)
  ssoConfigured.value = config.configured
  ssoEnabled.value = (config.status || 'ACTIVE') === 'ACTIVE'
  ssoForm.name = config.name || '企业登录'
  ssoForm.issuer = config.issuer || ''
  ssoForm.clientId = config.clientId || ''
  // 后端只返回掩码，留空即表示不修改
  ssoForm.clientSecret = ''
  ssoForm.scopes = config.scopes || 'openid profile email'
  ssoForm.redirectUri = config.redirectUri || ''
  ssoForm.nameClaim = config.nameClaim || ''
  ssoForm.emailClaim = config.emailClaim || ''
}

async function onSaveSso() {
  if (!ssoTenant.value) return
  if (!ssoForm.issuer.trim() || !ssoForm.clientId.trim()) {
    ElMessage.warning('Issuer 与 Client ID 为必填项')
    return
  }
  if (!ssoConfigured.value && !ssoForm.clientSecret.trim()) {
    ElMessage.warning('首次配置必须填写 Client Secret')
    return
  }
  ssoSaving.value = true
  try {
    const saved = await saveSsoConfig(ssoTenant.value.id, {
      name: ssoForm.name.trim() || '企业登录',
      issuer: ssoForm.issuer.trim(),
      clientId: ssoForm.clientId.trim(),
      clientSecret: ssoForm.clientSecret.trim() || undefined,
      scopes: ssoForm.scopes.trim() || undefined,
      redirectUri: ssoForm.redirectUri.trim() || undefined,
      nameClaim: ssoForm.nameClaim.trim() || undefined,
      emailClaim: ssoForm.emailClaim.trim() || undefined,
      status: ssoEnabled.value ? 'ACTIVE' : 'DISABLED',
    })
    ssoConfigured.value = saved.configured
    ssoForm.clientSecret = ''
    ElMessage.success('SSO 配置已保存')
    if (ssoTab.value === 'identities') await loadIdentities()
  } finally {
    ssoSaving.value = false
  }
}

async function onTestSso() {
  if (!ssoForm.issuer.trim()) {
    ElMessage.warning('请先填写 Issuer')
    return
  }
  ssoTesting.value = true
  try {
    ssoTest.value = await testSsoConnection(ssoTenant.value?.id || '', ssoForm.issuer.trim())
  } finally {
    ssoTesting.value = false
  }
}

function onSsoTabChange(name: string | number) {
  if (name === 'identities') loadIdentities()
}

async function loadIdentities() {
  if (!ssoTenant.value) return
  identities.value = await listSsoIdentities(ssoTenant.value.id, identityFilter.value || undefined)
}

async function openBind(row: SsoIdentity) {
  binding.value = row
  bindForm.username = ''
  bindForm.ssoOnly = false
  bindVisible.value = true
  if (ssoTenant.value) {
    candidates.value = await listSsoCandidates(ssoTenant.value.id)
  }
}

async function onBind() {
  if (!ssoTenant.value || !binding.value) return
  if (!bindForm.username) {
    ElMessage.warning('请选择要绑定的控制台账号')
    return
  }
  bindingSaving.value = true
  try {
    await bindSsoIdentity(ssoTenant.value.id, binding.value.id, bindForm.username, bindForm.ssoOnly)
    bindVisible.value = false
    ElMessage.success('绑定成功，该员工现在可以通过企业登录进入控制台')
    await loadIdentities()
  } finally {
    bindingSaving.value = false
  }
}

async function onUnbind(row: SsoIdentity) {
  if (!ssoTenant.value) return
  await unbindSsoIdentity(ssoTenant.value.id, row.id)
  ElMessage.success('已解绑')
  await loadIdentities()
}

function identityTagType(status: SsoIdentity['status']): 'success' | 'warning' | 'info' {
  if (status === 'ACTIVE') return 'success'
  if (status === 'PENDING') return 'warning'
  return 'info'
}

function identityStatusText(status: SsoIdentity['status']) {
  if (status === 'ACTIVE') return '已绑定'
  if (status === 'PENDING') return '待绑定'
  return '已解绑'
}

function fmtTime(value?: string) {
  return fmtDateTime(value)
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; align-items: center; justify-content: space-between; }
.toolbar-right { display: flex; align-items: center; gap: 8px; }
.code-cell { font-family: 'SFMono-Regular', Consolas, Menlo, monospace; }
.switch-alert { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.identity-toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; }
.identity-hint { margin: 10px 0 0; font-size: 12px; line-height: 1.6; color: var(--el-text-color-secondary); }
.dialog-footer-inline { display: flex; justify-content: flex-end; gap: 8px; }
.test-detail { margin-top: 6px; font-size: 12px; line-height: 1.7; word-break: break-all; }
.muted { color: var(--el-text-color-secondary); }
.bind-identity { line-height: 1.6; }
</style>
