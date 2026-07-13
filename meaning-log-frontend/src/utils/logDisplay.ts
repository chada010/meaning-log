export const formatLogTime = (value: string) => value?.replace('T', ' ').slice(0, 16)

export const previewLogContent = (value: string) => {
  const text = value.replace(/\s+/g, ' ').trim()
  return text.length > 96 ? `${text.slice(0, 96)}...` : text
}

export const splitLogTags = (value?: string) =>
  value?.split(',').map((tag) => tag.trim()).filter(Boolean) ?? []
