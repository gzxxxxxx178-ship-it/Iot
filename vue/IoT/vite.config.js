import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  build: {
    rollupOptions: {
      output: {
        // 将稳定的第三方依赖拆分为缓存友好的共享块，避免业务入口过大。
        manualChunks(id) {
          if (!id.includes('node_modules')) return undefined
          if (id.includes('/echarts/')) return 'vendor-echarts'
          if (id.includes('/element-plus/')) return 'vendor-element-plus'
          if (id.includes('/@element-plus/icons-vue/')) return 'vendor-element-icons'
          if (id.includes('/vue/') || id.includes('/vue-router/') || id.includes('/pinia/')) {
            return 'vendor-vue'
          }
          return 'vendor'
        },
      },
    },
  },
})
