<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="brand">easy-bpm</div>
      <el-menu :default-active="activeMenu" router background-color="#1d2530" text-color="#a7b1c2" active-text-color="#409eff">
        <el-menu-item index="/dashboard">📊 仪表盘</el-menu-item>
        <el-menu-item index="/flows">🧭 流程定义</el-menu-item>
        <el-menu-item index="/workbench/todo">📥 待办任务</el-menu-item>
        <el-menu-item index="/workbench/done">📤 已办任务</el-menu-item>
        <el-menu-item index="/workbench/submitted">📝 我发起的</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div class="page-title">{{ pageTitle }}</div>
        <el-dropdown>
          <span class="user-chip">
            <el-icon><User /></el-icon>
            {{ auth.user?.name || auth.user?.username }}
            <el-tag v-if="auth.isTenantAdmin" size="small" type="warning" style="margin-left: 6px">管理员</el-tag>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="onLogout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { User } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const activeMenu = computed(() => {
  if (route.path.startsWith('/flows')) return '/flows'
  if (route.path.startsWith('/instances')) return '/workbench/todo'
  return route.path
})
const pageTitle = computed(() => (route.meta.title as string) || '')

async function onLogout() {
  await auth.logout()
  router.push({ name: 'login' })
}
</script>

<style scoped>
.layout { height: 100%; }
.aside { background: #1d2530; }
.brand { color: #fff; font-size: 20px; font-weight: 700; padding: 20px 24px; letter-spacing: 1px; }
.aside :deep(.el-menu) { border-right: none; }
.header { display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #e4e7ed; background: #fff; }
.page-title { font-size: 16px; font-weight: 600; }
.user-chip { display: inline-flex; align-items: center; cursor: pointer; font-size: 14px; color: #303133; }
.main { background: #f5f7fa; }
</style>
