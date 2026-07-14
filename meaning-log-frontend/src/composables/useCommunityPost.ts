import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  fetchPostDetail,
  followUser,
  likePost,
  listComments,
  submitComment,
  unfollowUser,
  unlikePost,
  type CommunityComment,
  type PublicLogDetail,
} from '../api/community'

const PAGE_SIZE = 20

export const useCommunityPost = () => {
  const post = ref<PublicLogDetail | null>(null)
  const comments = ref<CommunityComment[]>([])
  const loadingPost = ref(false)
  const loadingComments = ref(false)
  const commentInput = ref('')
  const submitting = ref(false)

  const canSubmit = computed(
    () => Boolean(commentInput.value.trim()) && !submitting.value,
  )

  async function loadPost(publicLogId: number) {
    loadingPost.value = true
    try {
      const { data } = await fetchPostDetail(publicLogId)
      post.value = data
    } finally {
      loadingPost.value = false
    }
  }

  async function loadComments(publicLogId: number, page = 1) {
    loadingComments.value = true
    try {
      const { data } = await listComments(publicLogId, page, PAGE_SIZE)
      comments.value = data
    } finally {
      loadingComments.value = false
    }
  }

  async function toggleLike() {
    if (!post.value) return
    const target = post.value
    const original = { liked: target.liked, likeCount: target.likeCount }
    target.liked = !original.liked
    target.likeCount = Math.max(0, original.likeCount + (target.liked ? 1 : -1))
    try {
      const { data } = target.liked
        ? await likePost(target.publicLogId)
        : await unlikePost(target.publicLogId)
      target.liked = data.liked
      target.likeCount = data.likeCount
    } catch {
      target.liked = original.liked
      target.likeCount = original.likeCount
      ElMessage.error('操作失败, 请稍后重试')
    }
  }

  async function toggleFollow() {
    if (!post.value?.author) return
    const target = post.value
    const authorId = target.author.id
    const previous = target.followingAuthor
    target.followingAuthor = !previous
    try {
      if (!previous) {
        await followUser(authorId)
        ElMessage.success('关注成功')
      } else {
        await unfollowUser(authorId)
      }
    } catch {
      target.followingAuthor = previous
      ElMessage.error('操作失败, 请稍后重试')
    }
  }

  async function postComment() {
    if (!post.value) return
    if (!canSubmit.value) return
    submitting.value = true
    try {
      const { data } = await submitComment(post.value.publicLogId, commentInput.value.trim())
      comments.value = [data, ...comments.value]
      post.value.commentCount += 1
      commentInput.value = ''
      ElMessage.success('评论已发布')
    } catch {
      // http 拦截器已提示, 这里不重复
    } finally {
      submitting.value = false
    }
  }

  return {
    post,
    comments,
    loadingPost,
    loadingComments,
    commentInput,
    submitting,
    canSubmit,
    loadPost,
    loadComments,
    toggleLike,
    toggleFollow,
    postComment,
  }
}
