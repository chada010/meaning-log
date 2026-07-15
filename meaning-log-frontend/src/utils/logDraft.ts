import type { LogImageRequest, MeaningLogRequest } from '../api/logs'

interface DraftImageMetadata {
  fileName?: string
  caption?: string
  contentType?: string
}

interface StoredLogDraftValue extends Omit<MeaningLogRequest, 'images'> {
  imageMetadata?: DraftImageMetadata[]
  images?: LogImageRequest[]
}

interface StoredLogDraft {
  value: StoredLogDraftValue
  savedAt: number
}

export interface RestoredLogDraft extends Omit<MeaningLogRequest, 'content' | 'logDate' | 'images'> {
  content?: string
  logDate?: string
  imageCount: number
}

export type LogDraftWriteResult = 'saved' | 'removed' | 'quota-exceeded' | 'failed'

const isEmptyDraft = (value: MeaningLogRequest) => (
  !value.title?.trim()
  && !value.content?.trim()
  && !value.mood?.trim()
  && !value.favorite
)

const toImageMetadata = (images?: LogImageRequest[]): DraftImageMetadata[] | undefined => {
  if (!images?.length) return undefined
  return images.map(({ fileName, caption, contentType }) => ({ fileName, caption, contentType }))
}

export const createSafeLogDraft = (value: MeaningLogRequest): StoredLogDraft => ({
  value: {
    title: value.title,
    content: value.content,
    logDate: value.logDate,
    mood: value.mood,
    favorite: value.favorite,
    imageMetadata: toImageMetadata(value.images),
  },
  savedAt: Date.now(),
})

export const parseLogDraft = (raw: string): RestoredLogDraft | undefined => {
  const parsed = JSON.parse(raw) as Partial<StoredLogDraft>
  if (!parsed.value || typeof parsed.savedAt !== 'number') return undefined

  const imageCount = parsed.value.imageMetadata?.length ?? parsed.value.images?.length ?? 0
  return {
    title: parsed.value.title,
    content: parsed.value.content,
    logDate: parsed.value.logDate,
    mood: parsed.value.mood,
    favorite: parsed.value.favorite,
    imageCount,
  }
}

export const writeLogDraft = (
  storage: Storage,
  key: string,
  value: MeaningLogRequest,
): LogDraftWriteResult => {
  try {
    if (isEmptyDraft(value)) {
      storage.removeItem(key)
      return 'removed'
    }
    storage.setItem(key, JSON.stringify(createSafeLogDraft(value)))
    return 'saved'
  } catch (error) {
    const errorName = typeof error === 'object' && error !== null && 'name' in error
      ? String(error.name)
      : ''
    if (errorName === 'QuotaExceededError') {
      return 'quota-exceeded'
    }
    return 'failed'
  }
}
