<script setup lang="ts">
import { ChatDotRound, DataAnalysis, DocumentAdd, House, UserFilled } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from './stores/authStore'

const router = useRouter()
const authStore = useAuthStore()

const logout = () => {
  authStore.logout()
  router.push({ name: 'login' })
}

const handleAccountCommand = (command: string) => {
  if (command === 'logout') {
    logout()
  }
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

      <div v-if="authStore.isLoggedIn" class="header-account desktop-account">
        <span>{{ authStore.user?.username }}</span>
        <el-button size="small" plain @click="logout">退出</el-button>
      </div>

      <el-dropdown
        v-if="authStore.isLoggedIn"
        class="mobile-account"
        trigger="click"
        @command="handleAccountCommand"
      >
        <el-button
          class="mobile-account-trigger"
          circle
          :icon="UserFilled"
          title="账户菜单"
        />
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item disabled>{{ authStore.user?.username }}</el-dropdown-item>
            <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

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
        <el-icon><House /></el-icon>
        <span>日志</span>
      </RouterLink>
      <RouterLink class="mobile-create-tab" to="/logs/new">
        <el-icon><DocumentAdd /></el-icon>
        <span>新增</span>
      </RouterLink>
      <RouterLink to="/ai-reports">
        <el-icon><DataAnalysis /></el-icon>
        <span>报告</span>
      </RouterLink>
      <RouterLink to="/xiaoji">
        <el-icon><ChatDotRound /></el-icon>
        <span>小记</span>
      </RouterLink>
    </nav>
  </el-container>
</template>
