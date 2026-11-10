<template>
  <el-container class="layout">
    <el-aside :width="collapsed ? '64px' : '210px'" class="aside">
      <div class="brand" @click="router.push('/dashboard')">
        <div class="brand-logo">
          <svg viewBox="0 0 24 24" width="20" height="20" fill="none">
            <path d="M4 6a2 2 0 1 1 4 0 2 2 0 0 1-4 0Z" fill="#fff" />
            <path d="M4 18a2 2 0 1 1 4 0 2 2 0 0 1-4 0Z" fill="#fff" />
            <path d="M16 12a2 2 0 1 1 4 0 2 2 0 0 1-4 0Z" fill="#fff" />
            <path d="M7 6h5a3 3 0 0 1 3 3v1a2 2 0 0 0 2 2M7 18h5a3 3 0 0 0 3-3v-1a2 2 0 0 1 2-2" stroke="#fff" stroke-width="1.6" stroke-linecap="round" />
          </svg>
        </div>
        <span v-show="!collapsed" class="brand-name">easy-bpm</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="collapsed"
        :collapse-transition="false"
        router
        class="aside-menu"
      >
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon>
          <template #title>仪表盘</template>
        </el-menu-item>
        <el-menu-item index="/flows">
          <el-icon><Share /></el-icon>
          <template #title>流程定义</template>
        </el-menu-item>
        <el-menu-item index="/workbench/todo">
          <el-icon><Tickets /></el-icon>
          <template #title>待办任务</template>
        </el-menu-item>
        <el-menu-item index="/workbench/done">
          <el-icon><Finished /></el-icon>
          <template #title>已办任务</template>
        </el-menu-item>
        <el-menu-item index="/workbench/submitted">
          <el-icon><Promotion /></el-icon>
          <template #title>我发起的</template>
        </el-menu-item>
        <template v-if="auth.isTenantAdmin">
          <el-menu-item index="/org">
            <el-icon><OfficeBuilding /></el-icon>
            <template #title>组织管理</template>
          </el-menu-item>
          <el-menu-item index="/tenant">
            <el-icon><Key /></el-icon>
            <template #title>租户凭证</template>
          </el-menu-item>
          <el-menu-item index="/audit">
            <el-icon><Memo /></el-icon>
            <template #title>审计日志</template>
          </el-menu-item>
        </template>
      </el-menu>
      <div class="aside-footer">
        <el-icon class="collapse-btn" @click="toggleCollapse">
          <Expand v-if="collapsed" />
          <Fold v-else />
        </el-icon>
      </div>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="pageTitle && pageTitle !== '仪表盘'">{{ pageTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-dropdown trigger="click">
            <div class="user-chip">
              <el-avatar :size="30" class="user-avatar">{{ avatarChar }}</el-avatar>
              <span class="user-name">{{ auth.user?.name || auth.user?.username }}</span>
              <el-tag v-if="auth.isTenantAdmin" size="small" type="warning" effect="light" round>管理员</el-tag>
              <el-icon class="user-arrow"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>
                  <el-icon><User /></el-icon>{{ auth.user?.username }}
                </el-dropdown-item>
                <el-dropdown-item divided @click="onLogout">
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="main">
        <router-view v-slot="{ Component }">
          <transition name="fade-slide" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowDown,
  Expand,
  Finished,
  Fold,
  Key,
  Memo,
  Odometer,
  OfficeBuilding,
  Promotion,
  Share,
  SwitchButton,
  Tickets,
  User,
} from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const COLLAPSE_KEY = 'easy-bpm-sidebar-collapsed'
const collapsed = ref(localStorage.getItem(COLLAPSE_KEY) === '1')

function toggleCollapse() {
  collapsed.value = !collapsed.value
  localStorage.setItem(COLLAPSE_KEY, collapsed.value ? '1' : '0')
}

const activeMenu = computed(() => {
  if (route.path.startsWith('/flows')) return '/flows'
  if (route.path.startsWith('/instances')) return '/workbench/todo'
  return route.path
})
const pageTitle = computed(() => (route.meta.title as string) || '')
const avatarChar = computed(() => (auth.user?.name || auth.user?.username || 'U').slice(0, 1).toUpperCase())

async function onLogout() {
  await auth.logout()
  router.push({ name: 'login' })
}
</script>

<style scoped>
.layout { height: 100%; }

/* ---------- 侧边栏（vben 深色风格） ---------- */
.aside {
  display: flex;
  flex-direction: column;
  background: var(--vben-sidebar-bg);
  transition: width 0.2s ease;
  overflow: hidden;
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 56px;
  padding: 0 16px;
  cursor: pointer;
  flex-shrink: 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}
.brand-logo {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: linear-gradient(135deg, #0960bd 0%, #3aa0ff 100%);
  flex-shrink: 0;
}
.brand-name {
  color: #fff;
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 0.5px;
  white-space: nowrap;
}

.aside-menu {
  flex: 1;
  border-right: none;
  background: transparent;
  padding: 8px 0;
  overflow-y: auto;
  overflow-x: hidden;
}
.aside-menu:not(.el-menu--collapse) { width: 210px; }

/* vben 菜单项：圆角选中块 */
.aside-menu :deep(.el-menu-item) {
  height: 44px;
  line-height: 44px;
  margin: 2px 10px;
  border-radius: 8px;
  color: rgba(255, 255, 255, 0.65);
  background: transparent;
  transition: all 0.2s ease;
}
.aside-menu :deep(.el-menu-item .el-icon) {
  color: rgba(255, 255, 255, 0.65);
  transition: color 0.2s ease;
}
.aside-menu :deep(.el-menu-item:hover) {
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
}
.aside-menu :deep(.el-menu-item:hover .el-icon) { color: #fff; }
.aside-menu :deep(.el-menu-item.is-active) {
  background: var(--el-color-primary);
  color: #fff;
  box-shadow: 0 2px 8px rgba(9, 96, 189, 0.4);
}
.aside-menu :deep(.el-menu-item.is-active .el-icon) { color: #fff; }

/* 折叠时菜单项居中、去掉侧边距 */
.aside-menu.el-menu--collapse :deep(.el-menu-item) {
  margin: 2px 8px;
  padding: 0 !important;
  width: 48px;
}

.aside-footer {
  flex-shrink: 0;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 0 18px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}
.aside-menu.el-menu--collapse + .aside-footer { justify-content: center; padding: 0; }
.collapse-btn {
  color: rgba(255, 255, 255, 0.65);
  font-size: 18px;
  cursor: pointer;
  transition: color 0.2s, transform 0.2s;
}
.collapse-btn:hover { color: #fff; transform: scale(1.1); }

/* ---------- 头部 ---------- */
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
  padding: 0 20px;
  border-bottom: 1px solid #e8eaed;
  background: #fff;
}
.user-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 8px;
  transition: background 0.2s;
  outline: none;
}
.user-chip:hover { background: #f5f7fa; }
.user-avatar {
  background: var(--el-color-primary);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
}
.user-name { font-size: 14px; color: #1f2937; font-weight: 500; }
.user-arrow { color: #909399; font-size: 12px; }

/* ---------- 内容区 ---------- */
.main {
  background: var(--vben-content-bg);
  padding: 16px;
  overflow-y: auto;
}
</style>
