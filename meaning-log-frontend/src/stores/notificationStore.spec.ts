// @vitest-environment jsdom

import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchUnreadCount } from '../api/notifications'
import { StreamFetchError, subscribeSseEvents, type SseMessage } from '../api/stream'
import { useNotificationStore } from './notificationStore'

const { subscribeSseEventsMock } = vi.hoisted(() => ({ subscribeSseEventsMock: vi.fn() }))

vi.mock('../api/notifications', () => ({
  fetchNotifications: vi.fn(),
  fetchUnreadCount: vi.fn(),
  markAllNotificationsRead: vi.fn(),
  markNotificationRead: vi.fn(),
}))

vi.mock('../api/stream', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/stream')>()
  return { ...actual, subscribeSseEvents: subscribeSseEventsMock }
})

beforeEach(() => {
  setActivePinia(createPinia())
  vi.useFakeTimers()
  type UnreadResponse = Awaited<ReturnType<typeof fetchUnreadCount>>
  vi.mocked(fetchUnreadCount).mockResolvedValue({ data: { count: 2 } } as UnreadResponse)
  vi.mocked(subscribeSseEvents).mockImplementation(() => new Promise(() => undefined))
})

afterEach(() => {
  vi.useRealTimers()
  vi.clearAllMocks()
})

describe('notificationStore SSE 生命周期', () => {
  it('重复启动时只建立一个通知 SSE', () => {
    const store = useNotificationStore()

    store.start()
    store.start()

    expect(subscribeSseEvents).toHaveBeenCalledTimes(1)
  })

  it('收到 notification 事件后刷新未读数，ready 后停止轮询', async () => {
    let onEvent: ((message: SseMessage) => void) | undefined
    vi.mocked(subscribeSseEvents).mockImplementationOnce((_path, handler) => {
      onEvent = handler
      return new Promise(() => undefined)
    })
    const store = useNotificationStore()

    store.start()
    await Promise.resolve()
    onEvent?.({ event: 'ready', data: 'ok' })
    onEvent?.({ event: 'notification', data: '{"id":1}' })
    await Promise.resolve()

    expect(fetchUnreadCount).toHaveBeenCalledTimes(2)
    expect(vi.getTimerCount()).toBe(0)
  })

  it('SSE 断线后恢复 30 秒轮询并退避重连', async () => {
    let onEvent: ((message: SseMessage) => void) | undefined
    let rejectStream: ((error: unknown) => void) | undefined
    vi.mocked(subscribeSseEvents).mockImplementationOnce((_path, handler) => {
      onEvent = handler
      return new Promise((_, reject) => {
        rejectStream = reject
      })
    })
    const store = useNotificationStore()

    store.start()
    await Promise.resolve()
    onEvent?.({ event: 'ready', data: 'ok' })
    rejectStream?.(new StreamFetchError('connection', 'disconnected'))
    await Promise.resolve()
    await Promise.resolve()

    expect(fetchUnreadCount).toHaveBeenCalledTimes(2)
    await vi.advanceTimersByTimeAsync(1_000)
    expect(subscribeSseEvents).toHaveBeenCalledTimes(2)
    await vi.advanceTimersByTimeAsync(30_000)
    expect(fetchUnreadCount).toHaveBeenCalledTimes(3)
  })

  it('reset 时中止 SSE 并清理轮询和重连 timer', () => {
    let signal: AbortSignal | undefined
    vi.mocked(subscribeSseEvents).mockImplementationOnce((_path, _handler, currentSignal) => {
      signal = currentSignal
      return new Promise(() => undefined)
    })
    const store = useNotificationStore()

    store.start()
    store.reset()

    expect(signal?.aborted).toBe(true)
    expect(vi.getTimerCount()).toBe(0)
  })
})
