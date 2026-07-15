<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ChatDotRound, Check, UserFilled } from '@element-plus/icons-vue'
import { ElDrawer, ElIcon, ElSkeleton } from 'element-plus'
import { useNotificationStore } from '../../stores/notificationStore'
import type { NotificationItem } from '../../api/notifications'
import { formatRelativeTime } from '../../utils/relativeTime'

const router = useRouter()
const store = useNotificationStore()

const visible = computed<boolean>({
  get: () => store.drawerOpen,
  set: (value) => {
    if (value) {
      void store.openDrawer()
    } else {
      store.closeDrawer()
    }
  },
})

const nameOf = (item: NotificationItem) => item.actor?.username ?? '有人'

const contentSnippet = (raw: string | null) => {
  if (!raw) return ''
  const trimmed = raw.replace(/\s+/g, ' ').trim()
  return trimmed.length > 60 ? `${trimmed.slice(0, 60)}…` : trimmed
}

const messageOf = (item: NotificationItem) => {
  const who = nameOf(item)
  if (item.type === 'LIKE') return `${who} 赞了你的日志`
  if (item.type === 'FOLLOW') return `${who} 关注了你`
  if (item.type === 'COMMENT') {
    const snippet = contentSnippet(item.content)
    return snippet ? `${who} 评论了你：${snippet}` : `${who} 评论了你`
  }
  return `${who} 给你发了新消息`
}

const avatarLetter = (item: NotificationItem) => {
  const name = item.actor?.username ?? ''
  return name.trim().charAt(0).toUpperCase() || null
}

const handleClick = async (item: NotificationItem) => {
  await store.markOneRead(item.id)
  store.closeDrawer()

  if (item.type === 'FOLLOW' && item.actor?.id) {
    void router.push({ name: 'community-user', params: { id: item.actor.id } })
    return
  }
  if ((item.type === 'LIKE' || item.type === 'COMMENT') && item.publicLogId) {
    void router.push({ name: 'community-post', params: { id: item.publicLogId } })
    return
  }
  // 兜底: 没有可跳转的目标, 停留在原地即可
}

const handleMarkAll = async () => {
  if (!store.hasUnread && store.items.every((n) => n.read)) return
  await store.markAllRead()
}

const handleLoadMore = () => {
  void store.loadMore()
}

const initialLoading = computed(() => store.loading && !store.initialized)
</script>

<template>
  <el-drawer
    v-model="visible"
    class="notification-drawer"
    :with-header="false"
    size="420px"
    append-to-body
  >
    <div class="notification-drawer__inner">
      <header class="notification-drawer__header">
        <div>
          <p class="eyebrow">Inbox</p>
          <h2>通知</h2>
          <p class="notification-drawer__subtitle">
            {{ store.hasUnread ? `有 ${store.unreadCount} 条新消息等着你` : '暂时没有新消息' }}
          </p>
        </div>
        <button
          type="button"
          class="notification-drawer__mark-all"
          :disabled="!store.hasUnread"
          @click="handleMarkAll"
        >
          <el-icon><Check /></el-icon>
          <span>全部已读</span>
        </button>
      </header>

      <div v-if="initialLoading" class="notification-drawer__loading">
        <el-skeleton :rows="3" animated />
        <el-skeleton :rows="3" animated />
      </div>

      <div v-else-if="!store.items.length" class="notification-drawer__empty">
        <span class="notification-drawer__empty-icon">
          <el-icon><ChatDotRound /></el-icon>
        </span>
        <p>还没有新的通知</p>
        <small>有人赞你、评论你、关注你时，会先在这里出现。</small>
      </div>

      <ul v-else class="notification-list">
      <li
        v-for="item in store.items"
        :key="item.id"
        :class="['notification-item', { 'notification-item--unread': !item.read }]"
        @click="handleClick(item)"
      >
        <span class="notification-item__avatar" aria-hidden="true">
          <template v-if="avatarLetter(item)">{{ avatarLetter(item) }}</template>
          <el-icon v-else><UserFilled /></el-icon>
        </span>
        <div class="notification-item__body">
          <p class="notification-item__text">{{ messageOf(item) }}</p>
          <span class="notification-item__time">{{ formatRelativeTime(item.createdAt) }}</span>
        </div>
      </li>
    </ul>

      <div v-if="store.items.length && store.hasMore" class="notification-drawer__more">
        <button
          type="button"
          class="notification-drawer__more-btn"
          :disabled="store.loading"
          @click="handleLoadMore"
        >{{ store.loading ? '加载中…' : '加载更多' }}</button>
      </div>
      <p v-else-if="store.items.length" class="notification-drawer__end">— 已经到底了 —</p>
    </div>
  </el-drawer>
</template>
