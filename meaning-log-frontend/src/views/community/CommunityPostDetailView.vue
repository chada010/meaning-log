<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElButton, ElEmpty, ElIcon, ElInput, ElSkeleton, ElTag } from 'element-plus'
import { ArrowLeft, ChatDotRound, Pointer, UserFilled } from '@element-plus/icons-vue'
import { useCommunityPost } from '../../composables/useCommunityPost'
import { displayLogTitle } from '../../utils/logDisplay'

const props = defineProps<{ id: number }>()

const router = useRouter()
const post = useCommunityPost()

onMounted(async () => {
  await Promise.all([
    post.loadPost(props.id),
    post.loadComments(props.id),
  ])
})

const tagList = (tags: string | null | undefined) =>
  (tags ?? '').split(/[,，、\s]+/).map((t) => t.trim()).filter(Boolean).slice(0, 6)

const openProfile = (userId: number | undefined | null) => {
  if (!userId) return
  router.push({ name: 'community-user', params: { id: userId } })
}
</script>

<template>
  <div class="community-shell community-post">
    <button class="community-post__back" @click="router.back()">
      <el-icon><ArrowLeft /></el-icon>
      <span>返回</span>
    </button>

    <el-skeleton v-if="post.loadingPost.value && !post.post.value" :rows="6" animated />

    <article v-else-if="post.post.value" class="community-post__card">
      <header class="community-post__head">
        <div class="community-post__author" @click="openProfile(post.post.value.author?.id)">
          <el-icon class="community-post__avatar"><UserFilled /></el-icon>
          <div>
            <p class="community-post__username">@{{ post.post.value.author?.username ?? '匿名' }}</p>
            <p class="community-post__date">{{ post.post.value.logDate ?? '' }}</p>
          </div>
        </div>
        <el-button
          v-if="post.post.value.author && !post.post.value.followingAuthor"
          type="primary"
          plain
          size="small"
          @click="post.toggleFollow"
        >关注</el-button>
        <el-button
          v-else-if="post.post.value.author"
          size="small"
          @click="post.toggleFollow"
        >已关注</el-button>
      </header>

      <h1 class="community-post__title">
        {{ displayLogTitle(post.post.value) }}
      </h1>
      <div v-if="tagList(post.post.value.aiTags).length" class="community-post__tags">
        <el-tag
          v-for="t in tagList(post.post.value.aiTags)"
          :key="t"
          size="small"
          type="info"
          effect="plain"
        >{{ t }}</el-tag>
      </div>
      <p v-if="post.post.value.aiSummary" class="community-post__summary">
        {{ post.post.value.aiSummary }}
      </p>
      <pre class="community-post__content">{{ post.post.value.content }}</pre>

      <footer class="community-post__actions">
        <button
          :class="['community-post__stat', { liked: post.post.value.liked }]"
          @click="post.toggleLike"
        >
          <el-icon><Pointer /></el-icon>
          <span>{{ post.post.value.liked ? '已赞' : '点赞' }} · {{ post.post.value.likeCount }}</span>
        </button>
        <span class="community-post__stat">
          <el-icon><ChatDotRound /></el-icon>
          <span>评论 · {{ post.post.value.commentCount }}</span>
        </span>
      </footer>
    </article>

    <section class="community-post__comments">
      <h3>评论 ({{ post.comments.value.length }})</h3>
      <div class="community-post__composer">
        <el-input
          v-model="post.commentInput.value"
          type="textarea"
          :rows="2"
          maxlength="500"
          show-word-limit
          placeholder="友善互动, 支持一下作者"
        />
        <el-button
          type="primary"
          :loading="post.submitting.value"
          :disabled="!post.canSubmit.value"
          @click="post.postComment"
        >发表评论</el-button>
      </div>

      <el-skeleton v-if="post.loadingComments.value && post.comments.value.length === 0" :rows="3" animated />
      <el-empty v-else-if="post.comments.value.length === 0" description="来抢沙发吧" />

      <ul v-else class="community-comment-list">
        <li v-for="c in post.comments.value" :key="c.id" class="community-comment">
          <a class="community-comment__author" @click="openProfile(c.author?.id)">
            @{{ c.author?.username ?? '匿名' }}
          </a>
          <p class="community-comment__body">{{ c.content }}</p>
          <p class="community-comment__time">{{ c.createdAt?.replace('T', ' ').slice(0, 16) }}</p>
        </li>
      </ul>
    </section>
  </div>
</template>
