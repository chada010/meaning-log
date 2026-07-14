import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  build: {
    // 关掉 rolldown minifier: Vite 8 rolldown 早期版本在压缩时会把 top-level var 名
    // 与 import alias 冲突(如 vue-router 内的 `bt` 与 import 的 `bt as b`),
    // 导致运行时 "bt is not a function"。禁 minify 让 bundle 变大但功能正确。
    minify: false,
  },
})
