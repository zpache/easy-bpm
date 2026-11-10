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
        { path: 'flows', name: 'flows', component: () => import('@/views/flows/FlowList.vue'), meta: { title: '流程定义' } },
        { path: 'flows/:code/versions', name: 'flow-versions', component: () => import('@/views/flows/FlowVersions.vue'), meta: { title: '版本历史' } },
        { path: 'flows/:code/design', name: 'flow-design', component: () => import('@/views/flows/FlowDesigner.vue'), meta: { title: '审批链设计' } },
        { path: 'workbench/todo', name: 'todo', component: () => import('@/views/workbench/TaskList.vue'), meta: { title: '待办', tab: 'todo' } },
        { path: 'workbench/done', name: 'done', component: () => import('@/views/workbench/TaskList.vue'), meta: { title: '已办', tab: 'done' } },
        { path: 'workbench/submitted', name: 'submitted', component: () => import('@/views/workbench/SubmittedList.vue'), meta: { title: '我发起的' } },
        { path: 'instances/:instanceId', name: 'instance-detail', component: () => import('@/views/workbench/InstanceDetail.vue'), meta: { title: '审批详情' } },
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
  return true
})

router.afterEach((to) => {
  const title = to.meta.title as string | undefined
  document.title = title ? `${title} · easy-bpm` : 'easy-bpm 控制台'
})
