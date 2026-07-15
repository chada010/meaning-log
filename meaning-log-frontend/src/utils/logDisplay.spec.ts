import { describe, expect, it } from 'vitest'
import { displayLogTitle } from './logDisplay'

describe('displayLogTitle', () => {
  it('title 为 null 时统一回退到 AI 标题或占位文案', () => {
    expect(displayLogTitle({ title: null, aiTitle: 'AI 标题' })).toBe('AI 标题')
    expect(displayLogTitle({ title: null, aiTitle: null })).toBe('未命名的一天')
  })
})
