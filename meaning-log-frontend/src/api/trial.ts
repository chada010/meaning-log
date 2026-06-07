import http from './http'
import { applyLogAi, createLog, type AiSuggestion, type MeaningLogRequest } from './logs'

export interface TrialAiResult {
  title: string
  summary: string
  tags: string[]
}

interface PendingTrial {
  value: MeaningLogRequest
  ai?: TrialAiResult
}

const pendingKey = 'meaning-log-pending-trial'
const trialDraftKey = 'meaning-log-trial-draft'

export const analyzeTrialLog = (data: MeaningLogRequest) => {
  return http.post<TrialAiResult>('/trial/analyze', data)
}

export const savePendingTrial = (pending: PendingTrial) => {
  localStorage.setItem(pendingKey, JSON.stringify(pending))
}

export const hasPendingTrial = () => Boolean(localStorage.getItem(pendingKey))

const clearPendingTrial = () => {
  localStorage.removeItem(pendingKey)
  localStorage.removeItem(trialDraftKey)
}

/**
 * 注册/登录成功后调用：若存在试用草稿，把它（连同试用时看到的 AI 结果）
 * 落库为用户的第一条日志。返回新日志 id；没有待保存内容时返回 null。
 */
export const persistPendingTrial = async (): Promise<number | null> => {
  const raw = localStorage.getItem(pendingKey)
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
      // AI 结果落库失败不影响已保存的日志，用户之后可在详情页重新整理
    }
  }

  clearPendingTrial()
  return log.id
}
