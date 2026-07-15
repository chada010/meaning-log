import { PENDING_TRIAL_STORAGE_KEY, TRIAL_DRAFT_STORAGE_KEY } from '../constants/app'
import http from './http'
import { applyLogAi, createLog, type AiSuggestion, type MeaningLogRequest } from './logs'
import { streamFetchJson } from './stream'

export interface TrialAiResult {
  title: string
  summary: string
  tags: string[]
}

interface PendingTrial {
  value: MeaningLogRequest
  ai?: TrialAiResult
}

export type PendingTrialSaveStatus = 'saved' | 'quota-exceeded' | 'failed'

export interface PendingTrialSaveResult {
  status: PendingTrialSaveStatus
  omittedImageCount: number
}

export const analyzeTrialLog = (data: MeaningLogRequest) => {
  return http.post<TrialAiResult>('/trial/analyze', data)
}

export const analyzeTrialLogStream = (data: MeaningLogRequest, onChunk?: (chunk: string) => void) =>
  streamFetchJson<TrialAiResult>('/trial/analyze/stream', data, onChunk)

export const savePendingTrial = (pending: PendingTrial): PendingTrialSaveResult => {
  const omittedImageCount = pending.value.images?.length ?? 0
  const safePending: PendingTrial = {
    value: {
      title: pending.value.title,
      content: pending.value.content,
      logDate: pending.value.logDate,
      mood: pending.value.mood,
      favorite: pending.value.favorite,
    },
    ai: pending.ai,
  }
  try {
    localStorage.setItem(PENDING_TRIAL_STORAGE_KEY, JSON.stringify(safePending))
    return { status: 'saved', omittedImageCount }
  } catch (error) {
    const errorName = typeof error === 'object' && error !== null && 'name' in error
      ? String(error.name)
      : ''
    return {
      status: errorName === 'QuotaExceededError' ? 'quota-exceeded' : 'failed',
      omittedImageCount,
    }
  }
}

export const hasPendingTrial = () => Boolean(localStorage.getItem(PENDING_TRIAL_STORAGE_KEY))

const clearPendingTrial = () => {
  localStorage.removeItem(PENDING_TRIAL_STORAGE_KEY)
  localStorage.removeItem(TRIAL_DRAFT_STORAGE_KEY)
}

/**
 * 注册/登录成功后调用：若存在试用草稿，把它（连同试用时看到的 AI 结果）
 * 落库为用户的第一条日志。返回新日志 id；没有待保存内容时返回 null。
 */
export const persistPendingTrial = async (): Promise<number | null> => {
  const raw = localStorage.getItem(PENDING_TRIAL_STORAGE_KEY)
  if (!raw) {
    return null
  }

  let pending: PendingTrial
  try {
    pending = JSON.parse(raw) as PendingTrial
  } catch {
    clearPendingTrial()
    return null
  }

  if (!pending.value?.content?.trim()) {
    clearPendingTrial()
    return null
  }

  const { data: log } = await createLog(pending.value)

  if (pending.ai) {
    const suggestion: AiSuggestion = {
      title: pending.ai.title,
      summary: pending.ai.summary,
      tags: pending.ai.tags ?? [],
    }
    try {
      await applyLogAi(log.id, suggestion)
    } catch {
      // AI 结果落库失败不影响已保存的日志，用户之后可在详情页重新整理。
    }
  }

  clearPendingTrial()
  return log.id
}
