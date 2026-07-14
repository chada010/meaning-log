import http from './http'

export interface NotificationItem {
  id: number
  type: 'LIKE' | 'COMMENT' | 'FOLLOW'
  actor: { id: number; username: string } | null
  publicLogId: number | null
  commentId: number | null
  content: string | null
  read: boolean
  createdAt: string
}

export const fetchNotifications = (unreadOnly = false, page = 1, size = 20) =>
  http.get<NotificationItem[]>('/notifications', { params: { unreadOnly, page, size } })

export const fetchUnreadCount = () =>
  http.get<{ count: number }>('/notifications/unread-count')

export const markNotificationRead = (id: number) =>
  http.post<void>(`/notifications/${id}/read`)

export const markAllNotificationsRead = () =>
  http.post<void>('/notifications/read-all')
