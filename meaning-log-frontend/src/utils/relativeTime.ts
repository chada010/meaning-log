/**
 * 把 ISO 时间格式化成"刚刚 / N 分钟前 / N 小时前 / N 天前"。
 * 超过 7 天返回 YYYY-MM-DD。
 * 输入非法或为空时返回空串。
 */
export const formatRelativeTime = (iso: string | null | undefined): string => {
  if (!iso) return ''
  const value = new Date(iso)
  if (Number.isNaN(value.getTime())) return ''

  const diffSec = Math.max(0, Math.floor((Date.now() - value.getTime()) / 1000))
  if (diffSec < 60) return '刚刚'
  if (diffSec < 3600) return `${Math.floor(diffSec / 60)} 分钟前`
  if (diffSec < 86400) return `${Math.floor(diffSec / 3600)} 小时前`
  if (diffSec < 604800) return `${Math.floor(diffSec / 86400)} 天前`
  return value.toISOString().slice(0, 10)
}
