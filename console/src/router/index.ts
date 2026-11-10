import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('@/views/Login.vue') },
    {
      path: '/',
      component: () => import('@/layouts/AdminLayout.vue'),
      children: [
        { path: '', redirect: '/dashboard' },
        { path: 'dashboard', name: 'dashboard', component: () => import('@/views/Dashboard.vue'), meta: { title: '仪表盘' } },
        { path: 'platform', name: 'platform', component: () => import('@/views/Platform.vue'), meta: { title: '平台管理', requiresPlatformAdmin: true } },
        { path: 'flows', name: 'flows', component: () => import('@/views/flows/FlowList.vue'), meta: { title: '流程定义' } },
        { path: 'flows/:code/versions', name: 'flow-versions', component: () => import('@/views/flows/FlowVersions.vue'), meta: { title: '版本历史' } },
        { path: 'flows/:code/design', name: 'flow-design', component: () => import('@/views/flows/FlowDesigner.vue'), meta: { title: '审批流程设计' } },
        { path: 'workbench/todo', name: 'todo', component: () => import('@/views/workbench/TaskList.vue'), meta: { title: '待办', tab: 'todo' } },
        { path: 'workbench/done', name: 'done', component: () => import('@/views/workbench/TaskList.vue'), meta: { title: '已办', tab: 'done' } },
        { path: 'workbench/submitted', name: 'submitted', component: () => import('@/views/workbench/SubmittedList.vue'), meta: { title: '我发起的' } },
        { path: 'workbench/cc', name: 'cc', component: () => import('@/views/workbench/CcList.vue'), meta: { title: '抄送我的' } },
        { path: 'workbench/all-instances', name: 'all-instances', component: () => import('@/views/workbench/InstancesAdmin.vue'), meta: { title: '全部实例', requiresAdmin: true } },
        { path: 'instances/:instanceId', name: 'instance-detail', component: () => import('@/views/workbench/InstanceDetail.vue'), meta: { title: '审批详情' } },
        { path: 'org', name: 'org', component: () => import('@/views/Org.vue'), meta: { title: '组织管理', requiresAdmin: true } },
        { path: 'tenant', name: 'tenant', component: () => import('@/views/Tenant.vue'), meta: { title: '租户凭证', requiresAdmin: true } },
        { path: 'audit', name: 'audit', component: () => import('@/views/Audit.vue'), meta: { title: '审计日志', requiresAdmin: true } },
      ],
    },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.name !== 'login' && !auth.loggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && auth.loggedIn) {
    return { name: 'dashboard' }
  }
  if (to.matched.some((r) => r.meta.requiresAdmin) && !auth.isTenantAdmin) {
    return { name: 'dashboard' }
  }
  if (to.matched.some((r) => r.meta.requiresPlatformAdmin) && !auth.isPlatformAdmin) {
    return { name: 'dashboard' }
  }
  return true
})

router.afterEach((to) => {
  const title = to.meta.title as string | undefined
  document.title = title ? `${title} · easy-bpm` : 'easy-bpm 控制台'
})
