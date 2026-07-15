<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { UserFilled } from '@element-plus/icons-vue'
import { ElButton, ElDrawer, ElEmpty, ElIcon, ElSkeleton } from 'element-plus'
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
    title="通知"
    size="380px"
    append-to-body
  >
    <template #header>
      <div class="notification-drawer__header">
        <strong>通知</strong>
        <el-button
          text
          type="primary"
          :disabled="!store.hasUnread"
          @click="handleMarkAll"
        >全部已读</el-button>
      </div>
    </template>

    <div v-if="initialLoading" class="notification-drawer__loading">
      <el-skeleton :rows="3" animated />
      <el-skeleton :rows="3" animated />
    </div>

    <el-empty
      v-else-if="!store.items.length"
      description="还没有通知"
    />

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
      <el-button :loading="store.loading" plain @click="handleLoadMore">
        加载更多
      </el-button>
    </div>
    <p v-else-if="store.items.length" class="notification-drawer__end">— 已经到底了 —</p>
  </el-drawer>
</template>
