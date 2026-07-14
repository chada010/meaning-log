import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { fetchUnreadCount } from '../api/notifications'

const POLL_INTERVAL_MS = 30_000

export const useNotificationStore = defineStore('notification', () => {
  const unreadCount = ref(0)
  const drawerOpen = ref(false)

  let timer: number | null = null

  const hasUnread = computed(() => unreadCount.value > 0)

  async function refreshUnreadCount() {
    try {
      const { data } = await fetchUnreadCount()
      unreadCount.value = data.count ?? 0
    } catch {
      // 静默失败, 不打扰用户 (http 拦截器已提示网络异常)
    }
  }

  function startPolling() {
    if (timer !== null) return
    void refreshUnreadCount()
    timer = window.setInterval(refreshUnreadCount, POLL_INTERVAL_MS)
  }

  function stopPolling() {
    if (timer === null) return
    window.clearInterval(timer)
    timer = null
  }

  function decrementUnread(by = 1) {
    unreadCount.value = Math.max(0, unreadCount.value - by)
  }

  function clearUnread() {
    unreadCount.value = 0
  }

  function openDrawer() {
    drawerOpen.value = true
  }

  function closeDrawer() {
    drawerOpen.value = false
  }

  return {
    unreadCount,
    drawerOpen,
    hasUnread,
    refreshUnreadCount,
    startPolling,
    stopPolling,
    decrementUnread,
    clearUnread,
    openDrawer,
    closeDrawer,
  }
})
