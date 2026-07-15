import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  fetchNotifications,
  fetchUnreadCount,
  markAllNotificationsRead,
  markNotificationRead,
  type NotificationItem,
} from '../api/notifications'
import { StreamFetchError, subscribeSseEvents } from '../api/stream'

const POLL_INTERVAL_MS = 30_000
const RECONNECT_INITIAL_DELAY_MS = 1_000
const RECONNECT_MAX_DELAY_MS = 30_000
const PAGE_SIZE = 20

export const useNotificationStore = defineStore('notification', () => {
  const unreadCount = ref(0)
  const drawerOpen = ref(false)
  const items = ref<NotificationItem[]>([])
  const loading = ref(false)
  const page = ref(1)
  const hasMore = ref(true)
  const initialized = ref(false)

  let pollTimer: number | null = null
  let reconnectTimer: number | null = null
  let streamController: AbortController | null = null
  let reconnectDelay = RECONNECT_INITIAL_DELAY_MS
  let active = false

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
    if (pollTimer !== null) return
    void refreshUnreadCount()
    pollTimer = window.setInterval(refreshUnreadCount, POLL_INTERVAL_MS)
  }

  function stopPolling() {
    if (pollTimer === null) return
    window.clearInterval(pollTimer)
    pollTimer = null
  }

  function scheduleReconnect() {
    if (!active || reconnectTimer !== null) return
    const delay = reconnectDelay
    reconnectDelay = Math.min(reconnectDelay * 2, RECONNECT_MAX_DELAY_MS)
    reconnectTimer = window.setTimeout(() => {
      reconnectTimer = null
      connectStream()
    }, delay)
  }

  function connectStream() {
    if (!active || streamController !== null) return
    const controller = new AbortController()
    streamController = controller

    void subscribeSseEvents('/notifications/stream', ({ event }) => {
      if (event === 'ready') {
        reconnectDelay = RECONNECT_INITIAL_DELAY_MS
        stopPolling()
        return
      }
      if (event === 'notification') {
        void refreshUnreadCount()
      }
    }, controller.signal).catch((error: unknown) => {
      if (!active || controller.signal.aborted) return
      if (error instanceof StreamFetchError && error.status === 401) {
        stop()
        return
      }
      startPolling()
      scheduleReconnect()
    }).finally(() => {
      if (streamController === controller) {
        streamController = null
      }
    })
  }

  function start() {
    if (active) return
    active = true
    reconnectDelay = RECONNECT_INITIAL_DELAY_MS
    startPolling()
    connectStream()
  }

  function stop() {
    active = false
    stopPolling()
    if (reconnectTimer !== null) {
      window.clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
    streamController?.abort()
    streamController = null
    reconnectDelay = RECONNECT_INITIAL_DELAY_MS
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
    stop()
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
    start,
    stop,
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
