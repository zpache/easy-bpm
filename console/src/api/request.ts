import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import type { ApiResponse } from './types'
import { router } from '@/router'

const http = axios.create({ baseURL: import.meta.env.VITE_API_BASE || '/', timeout: 15000 })

// 产线通过独立 API 域名（VITE_API_BASE）访问时，请求不再携带 /api 前缀，
// 由产线网关/nginx 负责路由；开发环境未配置 VITE_API_BASE，走 vite 代理保留 /api。
const PROD_API_BASE = import.meta.env.VITE_API_BASE

http.interceptors.request.use((config) => {
  if (PROD_API_BASE && config.url?.startsWith('/api/')) {
    config.url = config.url.slice('/api'.length)
  }
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
