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
  </el-container>
</template>
