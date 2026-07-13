import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { MeaningLog } from '../api/logs'
import { getLogs } from '../api/logs'
import { useHomeLogs } from './useHomeLogs'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
}))

vi.mock('../api/logs', () => ({
  createLog: vi.fn(),
  deleteLog: vi.fn(),
  getAiTags: vi.fn(),
  getLogs: vi.fn(),
  updateLogFavorite: vi.fn(),
}))

const createLog = (id: number): MeaningLog => ({
  id,
  title: `日志 ${id}`,
  content: `内容 ${id}`,
  logDate: '2026-07-13',
  favorite: false,
  images: [],
  createdAt: '2026-07-13T10:00:00',
  updatedAt: '2026-07-13T10:00:00',
})

const deferred = <T>() => {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((resolver) => {
    resolve = resolver
  })
  return { promise, resolve }
}

describe('useHomeLogs.loadLogs', () => {
  beforeEach(() => {
    vi.mocked(getLogs).mockReset()
  })

  it('乱序完成时只采用最后一次请求结果', async () => {
    type LogResponse = Awaited<ReturnType<typeof getLogs>>
    const first = deferred<LogResponse>()
    const second = deferred<LogResponse>()
    vi.mocked(getLogs)
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise)
    const state = useHomeLogs()

    const firstLoad = state.loadLogs()
    const secondLoad = state.loadLogs()
    second.resolve({ data: [createLog(2)] } as LogResponse)
    await secondLoad
    first.resolve({ data: [createLog(1)] } as LogResponse)
    await firstLoad

    expect(state.logs.value.map((log) => log.id)).toEqual([2])
    expect(state.loading.value).toBe(false)
  })
})
