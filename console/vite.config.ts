import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 8091,
    proxy: {
      '/api': { target: 'http://127.0.0.1:8090', changeOrigin: true },
      '/open': { target: 'http://127.0.0.1:8090', changeOrigin: true },
    },
  },
})
