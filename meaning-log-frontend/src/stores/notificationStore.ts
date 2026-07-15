import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  fetchNotifications,
  fetchUnreadCount,
  markAllNotificationsRead,
  markNotificationRead,
  type NotificationItem,
} from '../api/notifications'

const POLL_INTERVAL_MS = 30_000
const PAGE_SIZE = 20

export const useNotificationStore = defineStore('notification', () => {
  const unreadCount = ref(0)
  const drawerOpen = ref(false)
  const items = ref<NotificationItem[]>([])
  const loading = ref(false)
  const page = ref(1)
  const hasMore = ref(true)
  const initialized = ref(false)

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

  function resetList() {
    items.value = []
    page.value = 1
    hasMore.value = true
    initialized.value = false
  }

  async function loadFirstPage() {
    loading.value = true
    try {
      const { data } = await fetchNotifications(false, 1, PAGE_SIZE)
      items.value = data
      page.value = 1
      hasMore.value = data.length === PAGE_SIZE
      initialized.value = true
    } finally {
      loading.value = false
    }
  }

  async function loadMore() {
    if (loading.value || !hasMore.value) return
    loading.value = true
    try {
      const next = page.value + 1
      const { data } = await fetchNotifications(false, next, PAGE_SIZE)
      items.value = items.value.concat(data)
      page.value = next
      hasMore.value = data.length === PAGE_SIZE
    } finally {
      loading.value = false
    }
  }

  async function markOneRead(id: number) {
    const target = items.value.find((n) => n.id === id)
    if (!target || target.read) return
    await markNotificationRead(id)
    target.read = true
    decrementUnread(1)
  }

  async function markAllRead() {
    await markAllNotificationsRead()
    items.value = items.value.map((n) => ({ ...n, read: true }))
    clearUnread()
  }

  async function openDrawer() {
    drawerOpen.value = true
    // 打开时刷新第一页 + 未读数, 保持面板内容最新
    await Promise.all([loadFirstPage(), refreshUnreadCount()])
  }

  function closeDrawer() {
    drawerOpen.value = false
  }

  function reset() {
    stopPolling()
    clearUnread()
    resetList()
    drawerOpen.value = false
  }

  return {
    unreadCount,
    drawerOpen,
    items,
    loading,
    hasMore,
    initialized,
    hasUnread,
    refreshUnreadCount,
    startPolling,
    stopPolling,
    decrementUnread,
    clearUnread,
    loadFirstPage,
    loadMore,
    markOneRead,
    markAllRead,
    openDrawer,
    closeDrawer,
    reset,
  }
})
