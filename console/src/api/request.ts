import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import type { ApiResponse } from './types'
import { router } from '@/router'

const http = axios.create({ baseURL: import.meta.env.VITE_API_BASE || '/', timeout: 15000 })

// 控制台接口路径与后端一致、无统一前缀（/auth/**、/flows/** 等）：
// - 开发环境：未配置 VITE_API_BASE，由 vite 按前缀代理到 8082（页面路由冲突的前缀按 Accept 头 bypass）；
// - 生产环境：配置 VITE_API_BASE（独立 API 域名），直接以同路径访问。
http.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.token) {
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResponse<unknown>
    if (body.code === 0) {
      return body.data as never
    }
    if (body.code === 40100) {
      useAuthStore().clear()
      router.push({ name: 'login' })
    }
    ElMessage.error(body.message || '请求失败')
    return Promise.reject(new Error(body.message))
  },
  (error) => {
    const message = error.response?.data?.message || error.message || '网络异常'
    ElMessage.error(message)
    return Promise.reject(error)
  },
)

export default http
