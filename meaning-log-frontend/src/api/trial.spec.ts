// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { PENDING_TRIAL_STORAGE_KEY } from '../constants/app'
import type { MeaningLogRequest } from './logs'
import { savePendingTrial } from './trial'

const draftWithImage = (): MeaningLogRequest => ({
  title: '试用日志',
  content: '需要在注册后保存的正文',
  logDate: '2026-07-15',
  images: [{
    fileName: 'photo.jpg',
    contentType: 'image/jpeg',
    dataUrl: `data:image/jpeg;base64,${'a'.repeat(2_790_000)}`,
  }],
})

beforeEach(() => {
  localStorage.clear()
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('游客待保存日志', () => {
  it('只保存文本和 AI 结果，不把 Base64 图片写入 localStorage', () => {
    const result = savePendingTrial({
      value: draftWithImage(),
      ai: { title: 'AI 标题', summary: 'AI 总结', tags: ['测试'] },
    })

    expect(result).toEqual({ status: 'saved', omittedImageCount: 1 })
    const raw = localStorage.getItem(PENDING_TRIAL_STORAGE_KEY) ?? ''
    expect(raw).not.toContain('data:image/jpeg;base64')
    expect(JSON.parse(raw)).toMatchObject({
      value: { title: '试用日志', content: '需要在注册后保存的正文' },
      ai: { title: 'AI 标题' },
    })
  })

  it('捕获 QuotaExceededError，调用方仍可继续导航', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('quota full', 'QuotaExceededError')
    })

    expect(savePendingTrial({ value: draftWithImage() })).toEqual({
      status: 'quota-exceeded',
      omittedImageCount: 1,
    })
  })
})
