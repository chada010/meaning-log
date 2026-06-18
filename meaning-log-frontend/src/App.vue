<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from './stores/authStore'

const router = useRouter()
const authStore = useAuthStore()

const logout = () => {
  authStore.logout()
  router.push({ name: 'login' })
}
</script>

<template>
  <el-container class="app-shell">
    <el-header class="app-header">
      <RouterLink class="brand" to="/">
        <span class="brand-mark">M</span>
        <span>
          <strong>Meaning Log</strong>
          <small>gentle daily notes</small>
        </span>
      </RouterLink>

      <nav v-if="authStore.isLoggedIn" class="header-nav">
        <RouterLink to="/">日志列表</RouterLink>
        <RouterLink to="/logs/new">新增日志</RouterLink>
        <RouterLink to="/ai-reports">AI 报告</RouterLink>
        <RouterLink to="/xiaoji">小记</RouterLink>
      </nav>

      <div v-if="authStore.isLoggedIn" class="header-account">
        <span>{{ authStore.user?.username }}</span>
        <el-button size="small" plain @click="logout">退出</el-button>
      </div>

      <nav v-else class="header-nav">
        <RouterLink to="/login">登录</RouterLink>
        <RouterLink to="/register">注册</RouterLink>
      </nav>
    </el-header>

    <el-main>
      <RouterView />
    </el-main>

    <nav v-if="authStore.isLoggedIn" class="mobile-tab-bar">
      <RouterLink to="/" :class="{ active: $route.path === '/' }">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <path d="M3 9.5L12 3l9 6.5V20a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9.5z"/>
          <path d="M9 21V12h6v9"/>
        </svg>
        <span>日志</span>
      </RouterLink>
      <RouterLink to="/logs/new">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="12" cy="12" r="9"/>
          <path d="M12 8v8M8 12h8"/>
        </svg>
        <span>新增</span>
      </RouterLink>
      <RouterLink to="/ai-reports">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
          <polyline points="14 2 14 8 20 8"/>
          <line x1="9" y1="13" x2="15" y2="13"/>
          <line x1="9" y1="17" x2="13" y2="17"/>
        </svg>
        <span>报告</span>
      </RouterLink>
      <RouterLink to="/xiaoji">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
          <path d="M8 10h.01M12 10h.01M16 10h.01"/>
        </svg>
        <span>小记</span>
      </RouterLink>
    </nav>
  </el-container>
</template>
