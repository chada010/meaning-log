export const formatLogTime = (value: string) => value?.replace('T', ' ').slice(0, 16)

export const previewLogContent = (value: string) => {
  const text = value.replace(/\s+/g, ' ').trim()
  return text.length > 96 ? `${text.slice(0, 96)}...` : text
}

export const splitLogTags = (value?: string) =>
  value?.split(',').map((tag) => tag.trim()).filter(Boolean) ?? []

/**
 * 展示日志标题：优先使用用户填写的 title，其次使用 AI 生成的 aiTitle，都没有则回退到占位文案。
 * 标题现在是可选字段，AI 分析后会自动填 aiTitle。
 */
export const displayLogTitle = (log: { title?: string; aiTitle?: string }) => {
  const title = log.title?.trim()
  if (title) {
    return title
  }
  const aiTitle = log.aiTitle?.trim()
  if (aiTitle) {
    return aiTitle
  }
  return '未命名的一天'
}

