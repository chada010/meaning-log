import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  build: {
    minify: false,
    rollupOptions: {
      output: {
        // Vite 8 rolldown 默认 chunk 拆分会把 vue-router 内部 helper 拆到 entry chunk,
        // 但 user 的 router chunk 先执行,helper 还没定义 -> "removeTrailingSlash is not a function"。
        // 显式把 vue-router / vue 单独 vendor chunk,保证内部完整性。
        manualChunks: {
          'vendor-vue-router': ['vue-router'],
          'vendor-vue': ['vue'],
          'vendor-pinia': ['pinia'],
        },
      },
    },
  },
})
