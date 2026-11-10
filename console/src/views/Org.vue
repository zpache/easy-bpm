<template>
  <el-card>
    <el-tabs v-model="tab">
      <!-- 人员 -->
      <el-tab-pane label="人员" name="users">
        <div class="toolbar">
          <el-input v-model="userQuery.keyword" placeholder="姓名 / 编码 / 手机号" clearable style="width: 220px" @keyup.enter="searchUsers" @clear="searchUsers" />
          <el-select v-model="userQuery.deptCode" placeholder="全部部门" clearable style="width: 180px; margin-left: 8px" @change="searchUsers">
            <el-option v-for="d in depts" :key="d.deptCode" :label="d.name" :value="d.deptCode" />
          </el-select>
          <el-select v-model="userQuery.status" placeholder="全部状态" clearable style="width: 130px; margin-left: 8px" @change="searchUsers">
            <el-option label="在职" value="ACTIVE" />
            <el-option label="离职" value="INACTIVE" />
          </el-select>
          <el-button style="margin-left: 8px" @click="searchUsers">搜索</el-button>
        </div>
        <el-table :data="users" v-loading="usersLoading">
          <template #empty><el-empty description="暂无组织数据，请先通过 OpenAPI /org/sync 同步" :image-size="80" /></template>
          <el-table-column prop="userCode" label="人员编码" width="120" />
          <el-table-column prop="name" label="姓名" width="110" />
          <el-table-column prop="deptName" label="部门" min-width="140" show-overflow-tooltip />
          <el-table-column prop="title" label="职位" width="120" />
          <el-table-column prop="leaderCode" label="直属主管" width="110" />
          <el-table-column prop="mobile" label="手机号" width="130" />
          <el-table-column prop="email" label="邮箱" min-width="160" show-overflow-tooltip />
          <el-table-column prop="status" label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small" effect="light" round>
                {{ row.status === 'ACTIVE' ? '在职' : row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="updatedAt" label="更新时间" width="180" />
        </el-table>
        <el-pagination
          v-model:current-page="userPage"
          :page-size="userSize"
          :total="userTotal"
          layout="total, prev, pager, next"
          style="margin-top: 12px"
          @current-change="loadUsers"
        />
      </el-tab-pane>

      <!-- 部门 -->
      <el-tab-pane label="部门" name="depts">
        <el-table :data="depts" v-loading="deptsLoading">
          <template #empty><el-empty description="暂无部门数据" :image-size="80" /></template>
          <el-table-column prop="deptCode" label="部门编码" width="160" />
          <el-table-column prop="name" label="部门名称" min-width="160" />
          <el-table-column prop="parentCode" label="上级部门" width="160">
            <template #default="{ row }">{{ row.parentCode || '-' }}</template>
          </el-table-column>
          <el-table-column prop="leaderId" label="部门主管" width="120">
            <template #default="{ row }">{{ row.leaderId || '-' }}</template>
          </el-table-column>
          <el-table-column prop="sortNo" label="排序" width="80" />
          <el-table-column prop="updatedAt" label="更新时间" width="180" />
        </el-table>
      </el-tab-pane>

      <!-- 角色 -->
      <el-tab-pane label="角色" name="roles">
        <el-table :data="roles" v-loading="rolesLoading">
          <template #empty><el-empty description="暂无角色数据" :image-size="80" /></template>
          <el-table-column prop="roleCode" label="角色编码" min-width="200" />
          <el-table-column prop="userCount" label="授权人数" width="140" />
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { orgDepts, orgRoles, orgUsers } from '@/api/org'
import type { OrgDeptView, OrgRoleView, OrgUserView } from '@/api/types'

const tab = ref('users')
const depts = ref<OrgDeptView[]>([])
const roles = ref<OrgRoleView[]>([])
const users = ref<OrgUserView[]>([])
const deptsLoading = ref(false)
const rolesLoading = ref(false)
const usersLoading = ref(false)
const userPage = ref(1)
const userSize = ref(10)
const userTotal = ref(0)
const userQuery = reactive({ keyword: '', deptCode: '', status: '' })

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

function searchUsers() {
  userPage.value = 1
  loadUsers()
}

onMounted(() => {
  loadUsers()
  loadDepts()
  loadRoles()
})
</script>

<style scoped>
.toolbar { display: flex; align-items: center; margin-bottom: 12px; }
</style>
