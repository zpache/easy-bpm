/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** API 基础地址：生产为独立域名（见 .env.production），开发走 vite 代理时留空 */
  readonly VITE_API_BASE?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<object, object, unknown>
  export default component
}
