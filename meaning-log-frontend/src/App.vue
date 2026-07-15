<script setup lang="ts">
import { onBeforeUnmount, onMounted, watch } from 'vue'
import { Bell, ChatDotRound, Connection, DataAnalysis, DocumentAdd, House, UserFilled } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from './stores/authStore'
import { useNotificationStore } from './stores/notificationStore'
import NotificationDrawer from './components/notifications/NotificationDrawer.vue'

const router = useRouter()
const authStore = useAuthStore()
const notificationStore = useNotificationStore()

const logout = () => {
  authStore.logout()
  notificationStore.reset()
  router.push({ name: 'login' })
}

const handleAccountCommand = (command: string) => {
  if (command === 'logout') {
    logout()
  }
}

const openNotificationDrawer = () => {
  void notificationStore.openDrawer()
}

onMounted(() => {
  if (authStore.isLoggedIn) {
    notificationStore.start()
  }
})

watch(
  () => authStore.isLoggedIn,
  (loggedIn) => {
    if (loggedIn) {
      notificationStore.start()
    } else {
      notificationStore.reset()
    }
  },
)

onBeforeUnmount(() => {
  notificationStore.stop()
})
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
        <RouterLink to="/community">社区</RouterLink>
        <RouterLink to="/ai-reports">AI 报告</RouterLink>
        <RouterLink to="/xiaoji">小记</RouterLink>
      </nav>

      <div v-if="authStore.isLoggedIn" class="header-tools">
        <el-badge
          :value="notificationStore.unreadCount"
          :hidden="!notificationStore.hasUnread"
          :max="99"
          class="header-bell"
        >
          <el-button
            circle
            plain
            size="small"
            :icon="Bell"
            title="通知"
            @click="openNotificationDrawer"
          />
        </el-badge>

        <div class="header-account desktop-account">
          <span>{{ authStore.user?.username }}</span>
          <el-button size="small" plain @click="logout">退出</el-button>
        </div>

        <el-dropdown
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
      </div>

      <nav v-else class="header-nav">
        <RouterLink to="/login">登录</RouterLink>
        <RouterLink to="/register">注册</RouterLink>
      </nav>
    </el-header>

    <el-main>
      <RouterView />
    </el-main>

    <nav v-if="authStore.isLoggedIn" class="mobile-tab-bar mobile-tab-bar--five">
      <RouterLink to="/" :class="{ active: $route.path === '/' }">
        <el-icon><House /></el-icon>
        <span>日志</span>
      </RouterLink>
      <RouterLink class="mobile-create-tab" to="/logs/new">
        <el-icon><DocumentAdd /></el-icon>
        <span>新增</span>
      </RouterLink>
      <RouterLink to="/community">
        <el-icon><Connection /></el-icon>
        <span>社区</span>
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

    <NotificationDrawer v-if="authStore.isLoggedIn" />
  </el-container>
</template>
