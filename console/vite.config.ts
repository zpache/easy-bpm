import { ZipArchive } from 'archiver'
import fs from 'node:fs'
import type { IncomingMessage } from 'node:http'
import { defineConfig, type Plugin } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

/**
 * 与 SPA 页面路由重名的接口前缀：整页导航/刷新（Accept: text/html）回退到 index.html，
 * 只有 XHR（Accept: application/json）才代理到后端。
 * 否则浏览器直接回车 /org、/tenant、/audit 等地址时，GET 请求会被代理到后端并被鉴权过滤器拒绝（缺少登录凭证）。
 */
const spaFallback = (req: IncomingMessage) =>
  req.headers.accept?.includes('text/html') ? '/index.html' : undefined

/**
 * 构建完成后自动把 dist/ 压缩为 dist.zip（供产线解压覆盖 nginx 目录）。
 * 与 easy-mall web-admin 的 viteArchiverPlugin 同机制：closeBundle 钩子 + archiver。
 */
function viteArchiverPlugin(): Plugin {
  return {
    name: 'vite-archiver',
    apply: 'build',
    closeBundle() {
      const distDir = fileURLToPath(new URL('./dist', import.meta.url))
      const zipPath = fileURLToPath(new URL('./dist.zip', import.meta.url))
      // 延迟一拍，确保 vite 完成最后的文件写入
      setTimeout(() => {
        const output = fs.createWriteStream(zipPath)
        const archive = new ZipArchive({ zlib: { level: 9 } })
        output.on('close', () => {
          const kb = (archive.pointer() / 1024).toFixed(0)
          console.log(`✓ 打包产物已压缩: dist.zip (${kb} KB)`)
        })
        archive.on('error', (err: Error) => {
          console.error('dist.zip 生成失败:', err)
        })
        archive.pipe(output)
        // 内容为 dist 目录内部（index.html 在 zip 根），解压即覆盖站点目录
        archive.directory(distDir, false)
        archive.finalize()
      }, 300)
    },
  }
}

export default defineConfig({
  plugins: [vue(), viteArchiverPlugin()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 8091,
    proxy: {
      // 控制台接口无前缀，按前缀代理到 8082。
      // /flows、/workbench 等与 SPA 页面路由重名：浏览器整页导航（Accept: text/html）回退到 SPA，
      // XHR（Accept: application/json）走代理；/auth/sso/authorize 是登录整页跳转，不 bypass。
      '/auth': { target: 'http://127.0.0.1:8082', changeOrigin: true },
      '/org': { target: 'http://127.0.0.1:8082', changeOrigin: true, bypass: spaFallback },
      '/tenant': { target: 'http://127.0.0.1:8082', changeOrigin: true, bypass: spaFallback },
      '/audit': { target: 'http://127.0.0.1:8082', changeOrigin: true, bypass: spaFallback },
      '/open': { target: 'http://127.0.0.1:8082', changeOrigin: true },
      '/public': { target: 'http://127.0.0.1:8082', changeOrigin: true },
      '/flows': {
        target: 'http://127.0.0.1:8082',
        changeOrigin: true,
        bypass: spaFallback,
      },
      '/workbench': {
        target: 'http://127.0.0.1:8082',
        changeOrigin: true,
        bypass: spaFallback,
      },
      '/instances': {
        target: 'http://127.0.0.1:8082',
        changeOrigin: true,
        bypass: spaFallback,
      },
      '/platform': {
        target: 'http://127.0.0.1:8082',
        changeOrigin: true,
        bypass: spaFallback,
      },
      '/dashboard': {
        target: 'http://127.0.0.1:8082',
        changeOrigin: true,
        bypass: spaFallback,
      },
    },
  },
})
