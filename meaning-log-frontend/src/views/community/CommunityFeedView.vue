<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElEmpty, ElIcon, ElSkeleton, ElTag } from 'element-plus'
import { ChatDotRound, Pointer, View } from '@element-plus/icons-vue'
import { useCommunityFeed } from '../../composables/useCommunityFeed'
import type { FeedType } from '../../api/community'
import { displayLogTitle } from '../../utils/logDisplay'
import { formatRelativeTime } from '../../utils/relativeTime'

const router = useRouter()
const feed = useCommunityFeed()

const TABS: Array<{ key: FeedType; label: string }> = [
  { key: 'hot', label: '热门' },
  { key: 'latest', label: '最新' },
  { key: 'following', label: '关注' },
]

onMounted(async () => {
  await feed.loadFirstPage()
})

const openDetail = (publicLogId: number) => {
  router.push({ name: 'community-post', params: { id: publicLogId } })
}

const openProfile = (userId: number, event: MouseEvent) => {
  event.stopPropagation()
  router.push({ name: 'community-user', params: { id: userId } })
}

const tagList = (tags: string | null) =>
  (tags ?? '').split(/[,，、\s]+/).map((t) => t.trim()).filter(Boolean).slice(0, 4)

const previewText = (item: { aiSummary: string | null; title: string | null }) =>
  item.aiSummary ?? item.title ?? ''

</script>

<template>
  <div class="community-shell">
    <header class="community-hero">
      <h2>社区</h2>
      <p>看看其他人今天写了什么</p>
    </header>

    <nav class="community-tabs">
      <button
        v-for="tab in TABS"
        :key="tab.key"
        :class="['community-tab', { active: feed.activeTab.value === tab.key }]"
        @click="feed.switchTab(tab.key)"
      >
        {{ tab.label }}
      </button>
    </nav>

    <section v-if="feed.loading.value && feed.items.value.length === 0" class="community-list">
      <el-skeleton :rows="4" animated />
      <el-skeleton :rows="4" animated />
    </section>

    <el-empty
      v-else-if="feed.isEmpty.value"
      :description="feed.activeTab.value === 'following' ? '还没有关注的人发帖' : '暂无内容'"
    />

    <section v-else class="community-list">
      <article
        v-for="item in feed.items.value"
        :key="item.publicLogId"
        class="community-card"
        @click="openDetail(item.publicLogId)"
      >
        <header class="community-card__head">
          <a class="community-card__author" @click="openProfile(item.author?.id ?? 0, $event)">
            @{{ item.author?.username ?? '匿名' }}
          </a>
          <span class="community-card__time">{{ formatRelativeTime(item.publishedAt) }}</span>
        </header>
        <h3 class="community-card__title">{{ displayLogTitle(item) }}</h3>
        <p class="community-card__excerpt">{{ previewText(item) }}</p>
        <div v-if="tagList(item.aiTags).length" class="community-card__tags">
          <el-tag
            v-for="t in tagList(item.aiTags)"
            :key="t"
            size="small"
            type="info"
            effect="plain"
          >{{ t }}</el-tag>
        </div>
        <footer class="community-card__foot" @click.stop>
          <button
            :class="['community-card__stat', { liked: item.liked }]"
            @click="feed.toggleLike(item)"
          >
            <el-icon><Pointer /></el-icon>
            <span>{{ item.likeCount }}</span>
          </button>
          <span class="community-card__stat">
            <el-icon><ChatDotRound /></el-icon>
            <span>{{ item.commentCount }}</span>
          </span>
          <span class="community-card__stat">
            <el-icon><View /></el-icon>
            <span>{{ item.viewCount }}</span>
          </span>
        </footer>
      </article>

      <div v-if="feed.hasMore.value" class="community-loadmore">
        <button :disabled="feed.loading.value" @click="feed.loadMore()">
          {{ feed.loading.value ? '加载中...' : '加载更多' }}
        </button>
      </div>
      <p v-else class="community-loadmore__end">— 到底了 —</p>
    </section>
  </div>
</template>
