import { ZipArchive } from 'archiver'
import fs from 'node:fs'
import { defineConfig, type Plugin } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

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
      '/api': { target: 'http://127.0.0.1:8082', changeOrigin: true },
      '/open': { target: 'http://127.0.0.1:8082', changeOrigin: true },
    },
  },
})
