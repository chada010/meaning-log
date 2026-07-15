import { describe, expect, it } from 'vitest'
import type { MeaningLogRequest } from '../api/logs'
import { parseLogDraft, writeLogDraft } from './logDraft'

class MemoryStorage implements Storage {
  private readonly values = new Map<string, string>()
  get length() { return this.values.size }
  clear() { this.values.clear() }
  getItem(key: string) { return this.values.get(key) ?? null }
  key(index: number) { return [...this.values.keys()][index] ?? null }
  removeItem(key: string) { this.values.delete(key) }
  setItem(key: string, value: string) { this.values.set(key, value) }
}

const largeImage = (index: number) => ({
  fileName: `photo-${index}.jpg`,
  caption: `说明 ${index}`,
  contentType: 'image/jpeg',
  dataUrl: `data:image/jpeg;base64,${'a'.repeat(2_790_000)}`,
})

describe('日志自动草稿', () => {
  it('三张接近 2MB 的图片不写入 localStorage，文本草稿仍可恢复', () => {
    const storage = new MemoryStorage()
    const draft: MeaningLogRequest = {
      title: '标题',
      content: '需要恢复的正文',
      logDate: '2026-07-15',
      images: [largeImage(1), largeImage(2), largeImage(3)],
    }

    expect(writeLogDraft(storage, 'draft', draft)).toBe('saved')
    const raw = storage.getItem('draft') ?? ''
    expect(raw.length).toBeLessThan(1_000)
    expect(raw).not.toContain('data:image/jpeg;base64')
    expect(parseLogDraft(raw)).toMatchObject({
      title: '标题',
      content: '需要恢复的正文',
      imageCount: 3,
    })
  })

  it('捕获 QuotaExceededError，不让草稿失败冒泡', () => {
    const storage = new MemoryStorage()
    storage.setItem = () => {
      throw new DOMException('quota full', 'QuotaExceededError')
    }

    expect(writeLogDraft(storage, 'draft', {
      content: '正文',
      logDate: '2026-07-15',
    })).toBe('quota-exceeded')
  })
})
