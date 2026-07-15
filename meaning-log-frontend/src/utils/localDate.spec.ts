import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest'
import { formatLocalDate, getRecentLocalDateRange } from './localDate'

beforeAll(() => {
  vi.stubEnv('TZ', 'Asia/Shanghai')
})

afterAll(() => {
  vi.unstubAllEnvs()
})

describe('本地业务日期', () => {
  it('上海凌晨使用当地今天，而不是 UTC 前一天', () => {
    const shanghaiMidnight = new Date('2026-07-14T16:30:00.000Z')

    expect(formatLocalDate(shanghaiMidnight)).toBe('2026-07-15')
    expect(shanghaiMidnight.toISOString().slice(0, 10)).toBe('2026-07-14')
  })

  it.each([
    [7, '2026-07-09'],
    [14, '2026-07-02'],
    [30, '2026-06-16'],
  ])('最近 %i 天区间使用本地起止日期', (days, expectedStart) => {
    const shanghaiMidnight = new Date('2026-07-14T16:30:00.000Z')

    expect(getRecentLocalDateRange(days, shanghaiMidnight)).toEqual([expectedStart, '2026-07-15'])
  })
})
