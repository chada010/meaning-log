<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElButton, ElEmpty, ElIcon, ElMessage, ElSkeleton } from 'element-plus'
import { ArrowLeft, UserFilled } from '@element-plus/icons-vue'
import {
  fetchUserPosts,
  fetchUserProfile,
  followUser,
  unfollowUser,
  type FeedItem,
  type UserProfile,
} from '../../api/community'
import { displayLogTitle } from '../../utils/logDisplay'

const props = defineProps<{ id: number }>()

const router = useRouter()
const profile = ref<UserProfile | null>(null)
const posts = ref<FeedItem[]>([])
const loadingProfile = ref(false)
const loadingPosts = ref(false)

const loadAll = async () => {
  loadingProfile.value = true
  loadingPosts.value = true
  try {
    const [profileResp, postsResp] = await Promise.all([
      fetchUserProfile(props.id),
      fetchUserPosts(props.id, 1, 20),
    ])
    profile.value = profileResp.data
    posts.value = postsResp.data
  } finally {
    loadingProfile.value = false
    loadingPosts.value = false
  }
}

onMounted(loadAll)
watch(() => props.id, loadAll)

const toggleFollow = async () => {
  if (!profile.value || profile.value.self) return
  const previous = profile.value.following
  profile.value.following = !previous
  profile.value.followerCount += previous ? -1 : 1
  try {
    if (!previous) {
      await followUser(props.id)
      ElMessage.success('关注成功')
    } else {
      await unfollowUser(props.id)
    }
  } catch {
    profile.value.following = previous
    profile.value.followerCount += previous ? 1 : -1
    ElMessage.error('操作失败, 请稍后重试')
  }
}

const openPost = (publicLogId: number) => {
  router.push({ name: 'community-post', params: { id: publicLogId } })
}

const previewText = (item: FeedItem) => item.aiSummary ?? item.title ?? ''
</script>

<template>
  <div class="community-shell community-profile">
    <button class="community-post__back" @click="router.back()">
      <el-icon><ArrowLeft /></el-icon>
      <span>返回</span>
    </button>

    <el-skeleton v-if="loadingProfile && !profile" :rows="4" animated />

    <section v-else-if="profile" class="community-profile__card">
      <div class="community-profile__head">
        <el-icon class="community-profile__avatar" :size="42"><UserFilled /></el-icon>
        <div class="community-profile__info">
          <h2>@{{ profile.user?.username ?? '匿名' }}</h2>
          <p class="community-profile__meta">
            帖子 {{ profile.postCount }} · 粉丝 {{ profile.followerCount }} · 关注 {{ profile.followingCount }}
          </p>
        </div>
        <el-button
          v-if="!profile.self && !profile.following"
          type="primary"
          plain
          @click="toggleFollow"
        >关注</el-button>
        <el-button v-else-if="!profile.self" @click="toggleFollow">已关注</el-button>
      </div>
    </section>

    <section class="community-profile__posts">
      <h3>Ta 的帖子</h3>
      <el-skeleton v-if="loadingPosts && posts.length === 0" :rows="3" animated />
      <el-empty v-else-if="posts.length === 0" description="还没发布过" />
      <article
        v-for="item in posts"
        v-else
        :key="item.publicLogId"
        class="community-card community-card--compact"
        @click="openPost(item.publicLogId)"
      >
        <h3 class="community-card__title">{{ displayLogTitle(item) }}</h3>
        <p class="community-card__excerpt">{{ previewText(item) }}</p>
        <div class="community-card__foot">
          <span class="community-card__stat">赞 {{ item.likeCount }}</span>
          <span class="community-card__stat">评论 {{ item.commentCount }}</span>
        </div>
      </article>
    </section>
  </div>
</template>
