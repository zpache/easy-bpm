<template>
  <el-card>
    <el-tabs v-model="tab">
      <!-- 人员 -->
      <el-tab-pane label="人员" name="users">
        <div class="toolbar">
          <div class="filters">
            <el-input v-model="userQuery.keyword" placeholder="姓名 / 编码 / 手机号" clearable style="width: 200px"
                      :prefix-icon="Search" @keyup.enter="searchUsers" @clear="searchUsers" />
            <el-select v-model="userQuery.deptCode" placeholder="全部部门" clearable filterable style="width: 160px" @change="searchUsers">
              <el-option v-for="d in depts" :key="d.deptCode" :label="d.name" :value="d.deptCode" />
            </el-select>
            <el-select v-model="userQuery.status" placeholder="全部状态" clearable style="width: 120px" @change="searchUsers">
              <el-option label="在职" value="ACTIVE" />
              <el-option label="离职" value="INACTIVE" />
            </el-select>
          </div>
          <el-button type="primary" @click="openUserDialog()">新增人员</el-button>
        </div>
        <el-table :data="users" v-loading="usersLoading">
          <template #empty><el-empty description="暂无人员数据" :image-size="80" /></template>
          <el-table-column prop="userCode" label="人员编码" width="120" />
          <el-table-column prop="name" label="姓名" width="110" />
          <el-table-column prop="deptName" label="部门" min-width="130" show-overflow-tooltip>
            <template #default="{ row }">{{ row.deptName || '-' }}</template>
          </el-table-column>
          <el-table-column prop="title" label="职位" width="110">
            <template #default="{ row }">{{ row.title || '-' }}</template>
          </el-table-column>
          <el-table-column prop="leaderCode" label="直属主管" min-width="130">
            <template #default="{ row }">
              <template v-if="effectiveLeaderOf(row)">
                {{ effectiveLeaderOf(row)!.name }}
                <el-tag v-if="!row.leaderCode" size="small" type="info" effect="plain" round>部门</el-tag>
              </template>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column prop="mobile" label="手机号" width="125">
            <template #default="{ row }">{{ row.mobile || '-' }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small" effect="light" round>
                {{ row.status === 'ACTIVE' ? '在职' : '离职' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="updatedAt" label="更新时间" width="160">
            <template #default="{ row }">{{ fmtDateTime(row.updatedAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openUserDialog(row)">编辑</el-button>
              <el-popconfirm title="删除后该人员的角色同步清除，确认？" width="240" @confirm="onDeleteUser(row)">
                <template #reference><el-button link type="danger">删除</el-button></template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination
            v-model:current-page="userPage"
            v-model:page-size="userSize"
            :total="userTotal"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            @current-change="loadUsers"
            @size-change="searchUsers"
          />
        </div>
      </el-tab-pane>

      <!-- 部门 -->
      <el-tab-pane label="部门" name="depts">
        <div class="toolbar">
          <span class="tab-tip">部门主管会自动作为部门内未单独设置主管的人员的直属主管（本部门未设则沿上级部门向上查找）；「直属主管 / 部门主管」类型审批节点均按此解析</span>
          <el-button type="primary" @click="openDeptDialog()">新增部门</el-button>
        </div>
        <el-table :data="depts" v-loading="deptsLoading">
          <template #empty><el-empty description="暂无部门数据" :image-size="80" /></template>
          <el-table-column prop="deptCode" label="部门编码" width="160" />
          <el-table-column prop="name" label="部门名称" min-width="160" />
          <el-table-column prop="parentCode" label="上级部门" width="160">
            <template #default="{ row }">{{ deptName(row.parentCode) }}</template>
          </el-table-column>
          <el-table-column prop="leaderId" label="部门主管" width="140">
            <template #default="{ row }">{{ leaderName(row.leaderId) }}</template>
          </el-table-column>
          <el-table-column prop="sortNo" label="排序" width="80" />
          <el-table-column prop="updatedAt" label="更新时间" width="160">
            <template #default="{ row }">{{ fmtDateTime(row.updatedAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDeptDialog(row)">编辑</el-button>
              <el-popconfirm title="删除后不可恢复，确认？" width="200" @confirm="onDeleteDept(row)">
                <template #reference><el-button link type="danger">删除</el-button></template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 角色 -->
      <el-tab-pane label="角色" name="roles">
        <div class="toolbar">
          <span class="tab-tip">角色用于「指定角色」审批节点；为角色分配或移除人员会实时影响后续审批人解析</span>
          <span />
        </div>
        <el-table :data="roles" v-loading="rolesLoading">
          <template #empty><el-empty description="暂无角色，可在人员编辑中创建角色并授权" :image-size="80" /></template>
          <el-table-column prop="roleCode" label="角色编码" min-width="200" />
          <el-table-column prop="userCount" label="授权人数" width="120" />
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openAssignDialog(row)">分配人员</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 人员编辑 -->
    <el-dialog v-model="userVisible" :title="editingUserCode ? '编辑人员' : '新增人员'" width="540px" :close-on-click-modal="false">
      <el-form :model="userForm" label-width="90px">
        <el-form-item label="人员编码" required>
          <el-input v-model="userForm.userCode" :disabled="!!editingUserCode" placeholder="全局唯一，创建后不可改" maxlength="64" />
        </el-form-item>
        <el-form-item label="姓名" required>
          <el-input v-model="userForm.name" maxlength="64" />
        </el-form-item>
        <el-form-item label="部门">
          <el-select v-model="userForm.deptCode" placeholder="选择部门" clearable filterable style="width: 100%">
            <el-option v-for="d in depts" :key="d.deptCode" :label="d.name" :value="d.deptCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="直属主管">
          <el-select v-model="userForm.leaderCode" :placeholder="deptLeaderHint || '留空默认取部门主管'" clearable filterable style="width: 100%">
            <el-option v-for="u in allUsers" :key="u.userCode" :label="`${u.name}(${u.userCode})`"
                       :value="u.userCode" :disabled="u.userCode === userForm.userCode" />
          </el-select>
          <div class="field-tip">不选时自动继承所在部门的主管（部门未设则沿上级部门向上查找）；仅跨部门汇报线需要显式指定</div>
        </el-form-item>
        <el-form-item label="职位">
          <el-input v-model="userForm.title" maxlength="64" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="userForm.mobile" maxlength="32" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="userForm.email" maxlength="128" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="userForm.status">
            <el-radio value="ACTIVE">在职</el-radio>
            <el-radio value="INACTIVE">离职</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="userForm.roleCodes" multiple filterable allow-create default-first-option
                     placeholder="输入或选择角色编码，可创建新角色" style="width: 100%">
            <el-option v-for="r in roles" :key="r.roleCode" :label="`${r.roleCode}(${r.userCount}人)`" :value="r.roleCode" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="userVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSaveUser">保存</el-button>
      </template>
    </el-dialog>

    <!-- 部门编辑 -->
    <el-dialog v-model="deptVisible" :title="editingDeptCode ? '编辑部门' : '新增部门'" width="480px" :close-on-click-modal="false">
      <el-form :model="deptForm" label-width="90px">
        <el-form-item label="部门编码" required>
          <el-input v-model="deptForm.deptCode" :disabled="!!editingDeptCode" placeholder="全局唯一，创建后不可改" maxlength="64" />
        </el-form-item>
        <el-form-item label="部门名称" required>
          <el-input v-model="deptForm.name" maxlength="128" />
        </el-form-item>
        <el-form-item label="上级部门">
          <el-select v-model="deptForm.parentCode" placeholder="不选则为顶级" clearable filterable style="width: 100%">
            <el-option v-for="d in depts.filter((x) => x.deptCode !== editingDeptCode)" :key="d.deptCode"
                       :label="d.name" :value="d.deptCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="部门主管">
          <el-select v-model="deptForm.leaderId" placeholder="选择人员" clearable filterable style="width: 100%">
            <el-option v-for="u in allUsers" :key="u.userCode" :label="`${u.name}(${u.userCode})`" :value="u.userCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="deptForm.sortNo" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="deptVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSaveDept">保存</el-button>
      </template>
    </el-dialog>

    <!-- 角色分配 -->
    <el-dialog v-model="assignVisible" :title="`分配角色：${assigningRole?.roleCode || ''}`" width="480px" :close-on-click-modal="false">
      <el-select v-model="assignUserCodes" multiple filterable placeholder="选择授权人员" style="width: 100%">
        <el-option v-for="u in allUsers" :key="u.userCode" :label="`${u.name}(${u.userCode})`" :value="u.userCode" />
      </el-select>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onAssign">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import {
  assignRole,
  deleteDept,
  deleteUser,
  orgDepts,
  orgRoleUsers,
  orgRoles,
  orgUsers,
  saveDept,
  saveUser,
} from '@/api/org'
import type { OrgDeptView, OrgRoleView, OrgUserView } from '@/api/types'
import { fmtDateTime } from '@/utils/format'

const tab = ref('users')
const depts = ref<OrgDeptView[]>([])
const roles = ref<OrgRoleView[]>([])
const users = ref<OrgUserView[]>([])
const allUsers = ref<OrgUserView[]>([])
const deptsLoading = ref(false)
const rolesLoading = ref(false)
const usersLoading = ref(false)
const saving = ref(false)
const userPage = ref(1)
const userSize = ref(10)
const userTotal = ref(0)
const userQuery = reactive({ keyword: '', deptCode: '', status: '' })

// 人员编辑
const userVisible = ref(false)
const editingUserCode = ref('')
const userForm = reactive({
  userCode: '', name: '', deptCode: '', leaderCode: '', title: '', mobile: '', email: '', status: 'ACTIVE', roleCodes: [] as string[],
})

// 部门编辑
const deptVisible = ref(false)
const editingDeptCode = ref('')
const deptForm = reactive({ deptCode: '', name: '', parentCode: '', leaderId: '', sortNo: 0 })

// 角色分配
const assignVisible = ref(false)
const assigningRole = ref<OrgRoleView | null>(null)
const assignUserCodes = ref<string[]>([])

const nameByCode = new Map<string, string>()

function leaderName(code?: string) {
  if (!code) return '-'
  return nameByCode.get(code) ? `${nameByCode.get(code)}(${code})` : code
}

/** 沿部门树向上找第一个配置了主管的部门（与后端 deptLeaderAbove 语义一致，excludeUserCode 避免自己审批自己） */
function deptLeaderAbove(deptCode?: string, excludeUserCode?: string): string | undefined {
  let current = deptCode
  const visited = new Set<string>()
  while (current && !visited.has(current)) {
    visited.add(current)
    const dept = depts.value.find((d) => d.deptCode === current)
    if (!dept) break
    if (dept.leaderId && dept.leaderId !== excludeUserCode) return dept.leaderId
    current = dept.parentCode
  }
  return undefined
}

/** 人员的生效直属主管：显式 leaderCode 优先，否则部门主管兜底 */
function effectiveLeaderOf(row: OrgUserView): { code: string; name: string } | undefined {
  const code = row.leaderCode || deptLeaderAbove(row.deptCode, row.userCode)
  if (!code) return undefined
  return { code, name: leaderName(code) }
}

/** 人员表单中「直属主管」的动态提示：显示当前所选部门的兜底主管 */
const deptLeaderHint = computed(() => {
  if (!userForm.deptCode) return ''
  const leader = deptLeaderAbove(userForm.deptCode, userForm.userCode)
  if (!leader) return ''
  return `留空默认取部门主管：${leaderName(leader)}`
})

function deptName(code?: string) {
  if (!code) return '-'
  return depts.value.find((d) => d.deptCode === code)?.name ?? code
}

async function loadDepts() {
  deptsLoading.value = true
  try {
    depts.value = await orgDepts()
  } finally {
    deptsLoading.value = false
  }
}

async function loadRoles() {
  rolesLoading.value = true
  try {
    roles.value = await orgRoles()
  } finally {
    rolesLoading.value = false
  }
}

async function loadUsers() {
  usersLoading.value = true
  try {
    const result = await orgUsers({
      page: userPage.value,
      size: userSize.value,
      keyword: userQuery.keyword || undefined,
      deptCode: userQuery.deptCode || undefined,
      status: userQuery.status || undefined,
    })
    users.value = result.list
    userTotal.value = result.total
  } finally {
    usersLoading.value = false
  }
}

/** 供主管/角色分配选择的全量人员（控制台单页最多取 100 人，适用于中小规模组织） */
async function loadAllUsers() {
  try {
    allUsers.value = (await orgUsers({ page: 1, size: 100 })).list
    nameByCode.clear()
    allUsers.value.forEach((u) => nameByCode.set(u.userCode, u.name))
  } catch {
    /* 选择数据尽力而为 */
  }
}

function searchUsers() {
  userPage.value = 1
  loadUsers()
}

// ---------- 人员 ----------
function openUserDialog(row?: OrgUserView) {
  editingUserCode.value = row?.userCode || ''
  userForm.userCode = row?.userCode || ''
  userForm.name = row?.name || ''
  userForm.deptCode = row?.deptCode || ''
  userForm.leaderCode = row?.leaderCode || ''
  userForm.title = row?.title || ''
  userForm.mobile = row?.mobile || ''
  userForm.email = row?.email || ''
  userForm.status = row?.status || 'ACTIVE'
  userVisible.value = true
}

async function onSaveUser() {
  if (!userForm.userCode.trim() || !userForm.name.trim()) {
    ElMessage.warning('请填写人员编码和姓名')
    return
  }
  saving.value = true
  try {
    await saveUser({
      userCode: userForm.userCode.trim(),
      name: userForm.name.trim(),
      deptCode: userForm.deptCode || undefined,
      leaderCode: userForm.leaderCode || undefined,
      title: userForm.title || undefined,
      mobile: userForm.mobile || undefined,
      email: userForm.email || undefined,
      status: userForm.status,
      roleCodes: userForm.roleCodes,
    })
    userVisible.value = false
    ElMessage.success('已保存')
    await Promise.all([loadUsers(), loadRoles(), loadAllUsers()])
  } finally {
    saving.value = false
  }
}

async function onDeleteUser(row: OrgUserView) {
  await deleteUser(row.userCode)
  ElMessage.success('已删除')
  await Promise.all([loadUsers(), loadRoles(), loadAllUsers()])
}

// ---------- 部门 ----------
function openDeptDialog(row?: OrgDeptView) {
  editingDeptCode.value = row?.deptCode || ''
  deptForm.deptCode = row?.deptCode || ''
  deptForm.name = row?.name || ''
  deptForm.parentCode = row?.parentCode || ''
  deptForm.leaderId = row?.leaderId || ''
  deptForm.sortNo = row?.sortNo ?? 0
  deptVisible.value = true
}

async function onSaveDept() {
  if (!deptForm.deptCode.trim() || !deptForm.name.trim()) {
    ElMessage.warning('请填写部门编码和名称')
    return
  }
  saving.value = true
  try {
    await saveDept({
      deptCode: deptForm.deptCode.trim(),
      name: deptForm.name.trim(),
      parentCode: deptForm.parentCode || undefined,
      leaderId: deptForm.leaderId || undefined,
      sortNo: deptForm.sortNo,
    })
    deptVisible.value = false
    ElMessage.success('已保存')
    await Promise.all([loadDepts(), loadUsers(), loadAllUsers()])
  } finally {
    saving.value = false
  }
}

async function onDeleteDept(row: OrgDeptView) {
  await deleteDept(row.deptCode)
  ElMessage.success('已删除')
  await loadDepts()
}

// ---------- 角色分配 ----------
async function openAssignDialog(row: OrgRoleView) {
  assigningRole.value = row
  try {
    assignUserCodes.value = await orgRoleUsers(row.roleCode)
  } catch {
    assignUserCodes.value = []
  }
  assignVisible.value = true
}

async function onAssign() {
  if (!assigningRole.value) return
  saving.value = true
  try {
    const result = await assignRole(assigningRole.value.roleCode, assignUserCodes.value)
    assignVisible.value = false
    ElMessage.success(`已保存：新增 ${result.added} 人，移除 ${result.removed} 人`)
    await loadRoles()
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadUsers()
  loadDepts()
  loadRoles()
  loadAllUsers()
})
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  gap: 8px;
}
.filters { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.tab-tip { font-size: 12px; color: #8a919f; }
.field-tip { font-size: 12px; color: #8a919f; line-height: 1.4; margin-top: 2px; }
.pager { display: flex; justify-content: flex-end; margin-top: 12px; }
</style>
