import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  fetchFeed,
  likePost,
  unlikePost,
  type FeedItem,
  type FeedType,
} from '../api/community'

const PAGE_SIZE = 20

export const useCommunityFeed = () => {
  const activeTab = ref<FeedType>('hot')
  const items = ref<FeedItem[]>([])
  const loading = ref(false)
  const page = ref(1)
  const hasMore = ref(true)

  const isEmpty = computed(() => !loading.value && items.value.length === 0)

  async function loadFirstPage() {
    page.value = 1
    hasMore.value = true
    loading.value = true
    try {
      const { data } = await fetchFeed(activeTab.value, 1, PAGE_SIZE)
      items.value = data
      hasMore.value = data.length === PAGE_SIZE
    } finally {
      loading.value = false
    }
  }

  async function loadMore() {
    if (loading.value || !hasMore.value) return
    loading.value = true
    try {
      const next = page.value + 1
      const { data } = await fetchFeed(activeTab.value, next, PAGE_SIZE)
      if (data.length === 0) {
        hasMore.value = false
        return
      }
      items.value = [...items.value, ...data]
      page.value = next
      hasMore.value = data.length === PAGE_SIZE
    } finally {
      loading.value = false
    }
  }

  async function switchTab(tab: FeedType) {
    if (tab === activeTab.value) return
    activeTab.value = tab
    await loadFirstPage()
  }

  async function toggleLike(item: FeedItem) {
    const original = { liked: item.liked, likeCount: item.likeCount }
    // 乐观更新
    item.liked = !original.liked
    item.likeCount = Math.max(0, original.likeCount + (item.liked ? 1 : -1))
    try {
      const { data } = item.liked
        ? await likePost(item.publicLogId)
        : await unlikePost(item.publicLogId)
      item.liked = data.liked
      item.likeCount = data.likeCount
    } catch {
      item.liked = original.liked
      item.likeCount = original.likeCount
      ElMessage.error('操作失败, 请稍后重试')
    }
  }

  return {
    activeTab,
    items,
    loading,
    hasMore,
    isEmpty,
    loadFirstPage,
    loadMore,
    switchTab,
    toggleLike,
  }
}
