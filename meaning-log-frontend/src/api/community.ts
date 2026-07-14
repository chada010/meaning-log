import http from './http'

export interface AuthorInfo {
  id: number
  username: string
}

export interface FeedItem {
  publicLogId: number
  logId: number
  author: AuthorInfo
  title: string | null
  aiTitle: string | null
  aiSummary: string | null
  aiTags: string | null
  logDate: string | null
  likeCount: number
  commentCount: number
  viewCount: number
  liked: boolean
  followingAuthor: boolean
  publishedAt: string
}

export interface PublicLogDetail extends FeedItem {
  content: string | null
  mood: string | null
}

export interface CommunityComment {
  id: number
  publicLogId: number
  author: AuthorInfo
  content: string
  parentId: number | null
  createdAt: string
}

export interface UserProfile {
  user: AuthorInfo
  followerCount: number
  followingCount: number
  postCount: number
  following: boolean
  self: boolean
}

export interface PublishResponse {
  publicLogId: number
  logId: number
  publishedAt: string
}

export interface LikeResponse {
  liked: boolean
  likeCount: number
}

export interface FollowResponse {
  following: boolean
  changed: boolean
}

export type FeedType = 'hot' | 'latest' | 'following'

export const publishLog = (logId: number) =>
  http.post<PublishResponse>(`/community/publish/${logId}`)

export const unpublishLog = (logId: number) =>
  http.delete<void>(`/community/publish/${logId}`)

export const fetchFeed = (type: FeedType, page = 1, size = 20) =>
  http.get<FeedItem[]>('/community/feed', { params: { type, page, size } })

export const fetchPostDetail = (publicLogId: number) =>
  http.get<PublicLogDetail>(`/community/posts/${publicLogId}`)

export const likePost = (publicLogId: number) =>
  http.post<LikeResponse>(`/community/like/${publicLogId}`)

export const unlikePost = (publicLogId: number) =>
  http.delete<LikeResponse>(`/community/like/${publicLogId}`)

export const listComments = (publicLogId: number, page = 1, size = 20) =>
  http.get<CommunityComment[]>(`/community/comments/${publicLogId}`, { params: { page, size } })

export const submitComment = (publicLogId: number, content: string, parentId: number | null = null) =>
  http.post<CommunityComment>(`/community/comments/${publicLogId}`, { content, parentId })

export const followUser = (userId: number) =>
  http.post<FollowResponse>(`/community/follow/${userId}`)

export const unfollowUser = (userId: number) =>
  http.delete<FollowResponse>(`/community/follow/${userId}`)

export const fetchUserProfile = (userId: number) =>
  http.get<UserProfile>(`/community/users/${userId}`)

export const fetchUserPosts = (userId: number, page = 1, size = 20) =>
  http.get<FeedItem[]>(`/community/users/${userId}/posts`, { params: { page, size } })
