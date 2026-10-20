import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5431,
    // 端口被占用时直接失败，避免实际端口和代理配置不一致
    strictPort: true,
    proxy: {
      // 后端 context-path 为 /api，通过代理转发可绕开后端 CORS 限制（前端无需改后端）
      '/api': {
        target: 'http://localhost:8123',
        changeOrigin: true
      }
    }
  },
  preview: {
    port: 5431,
    strictPort: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8123',
        changeOrigin: true
      }
    }
  }
})
