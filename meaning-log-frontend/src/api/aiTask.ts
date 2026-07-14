import http from './http'
import { StreamFetchError, subscribeSseDoneEvent } from './stream'
import type { AiChatResponse, AiReport, AiSuggestion } from './logs'

export type AiTaskType = 'LOG_ANALYZE' | 'LOG_REFINE' | 'REPORT_GENERATE' | 'REPORT_REFINE' | 'CHAT'
export type AiTaskStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED'

export interface AiTaskCreatedResponse {
  taskId: number
  taskType: AiTaskType
  status: AiTaskStatus
}

export interface AiTaskResponse {
  id: number
  taskType: AiTaskType
  status: AiTaskStatus
  resultJson?: string
  errorMessage?: string
  retryCount?: number
  createdAt?: string
  updatedAt?: string
}

const POLL_INITIAL_INTERVAL_MS = 3_000
const POLL_MAX_INTERVAL_MS = 30_000
const POLL_BACKOFF_MULTIPLIER = 2
const POLL_TIMEOUT_MS = 5 * 60 * 1_000

const AI_UNAVAILABLE_PREFIX = 'AI_UNAVAILABLE:'

export const createLogAnalyzeTask = (logId: number) =>
  http.post<AiTaskCreatedResponse>(`/logs/${logId}/ai`)

export const createLogRefineTask = (logId: number, message: string) =>
  http.post<AiTaskCreatedResponse>(`/logs/${logId}/ai/chat`, { message })

export const createDailySummaryTask = (date: string) =>
  http.post<AiTaskCreatedResponse>('/logs/ai/daily-summary', undefined, { params: { date } })

export const createPeriodReportTask = (startDate: string, endDate: string, title: string) =>
  http.post<AiTaskCreatedResponse>('/logs/ai/report', undefined, {
    params: { startDate, endDate, title },
  })

export const createReportRefineTask = (reportId: number, message: string) =>
  http.post<AiTaskCreatedResponse>(`/logs/ai/reports/${reportId}/chat`, { message })

export const createXiaojiChatTask = (message: string, sessionId?: number) =>
  http.post<AiTaskCreatedResponse>('/xiaoji/chat', { message, sessionId })

export const getAiTaskStatus = (taskId: number) =>
  http.get<AiTaskResponse>(`/ai/tasks/${taskId}`)

const isTerminal = (status: AiTaskStatus) => status === 'SUCCESS' || status === 'FAILED'

class AiTaskFailedError extends Error {
  readonly taskId: number
  readonly errorMessage?: string
  readonly aiUnavailable: boolean
  constructor(taskId: number, errorMessage?: string) {
    super(errorMessage || 'AI task failed')
    this.taskId = taskId
    this.errorMessage = errorMessage
    this.aiUnavailable = errorMessage?.startsWith(AI_UNAVAILABLE_PREFIX) ?? false
    this.name = 'AiTaskFailedError'
  }
}

async function waitForTaskDone(taskId: number): Promise<AiTaskResponse> {
  let pollStop: (() => void) | undefined
  let sseStop: (() => void) | undefined

  const pollLoop = new Promise<AiTaskResponse>((resolve, reject) => {
    const started = Date.now()
    let cancelled = false
    let timer: ReturnType<typeof setTimeout> | null = null
    let nextInterval = POLL_INITIAL_INTERVAL_MS

    const tick = async () => {
      if (cancelled) return
      try {
        const { data } = await getAiTaskStatus(taskId)
        if (isTerminal(data.status)) {
          cancelled = true
          resolve(data)
          return
        }
        if (Date.now() - started > POLL_TIMEOUT_MS) {
          cancelled = true
          reject(new Error('AI task timed out'))
          return
        }
        const delay = nextInterval
        nextInterval = Math.min(nextInterval * POLL_BACKOFF_MULTIPLIER, POLL_MAX_INTERVAL_MS)
        timer = setTimeout(tick, delay)
      } catch (error) {
        cancelled = true
        reject(error)
      }
    }

    pollStop = () => {
      cancelled = true
      if (timer) clearTimeout(timer)
    }

    timer = setTimeout(tick, nextInterval)
  })

  const ssePromise = new Promise<AiTaskResponse>((resolve, reject) => {
    subscribeSseDoneEvent(`/ai/tasks/${taskId}/stream`)
      .then(({ cancel, done }) => {
        sseStop = cancel
        done
          .then(async () => {
            try {
              const { data } = await getAiTaskStatus(taskId)
              resolve(data)
            } catch (error) {
              reject(error)
            }
          })
          .catch((error) => {
            if (error instanceof StreamFetchError) {
              reject(error)
            } else {
              reject(error)
            }
          })
      })
      .catch(reject)
  })

  try {
    const result = await Promise.any([ssePromise, pollLoop])
    return result
  } finally {
    pollStop?.()
    sseStop?.()
  }
}

async function runTask<T>(
  createResponse: { data: AiTaskCreatedResponse },
  parse: (resultJson: string) => T,
): Promise<T> {
  const { taskId, status } = createResponse.data
  if (status === 'SUCCESS' || status === 'FAILED') {
    const { data } = await getAiTaskStatus(taskId)
    return finalize(data, parse)
  }
  const finalTask = await waitForTaskDone(taskId)
  return finalize(finalTask, parse)
}

function finalize<T>(task: AiTaskResponse, parse: (resultJson: string) => T): T {
  if (task.status === 'FAILED') {
    throw new AiTaskFailedError(task.id, task.errorMessage)
  }
  if (!task.resultJson) {
    throw new Error('AI task returned no result')
  }
  return parse(task.resultJson)
}

const parseSuggestion = (json: string) => JSON.parse(json) as AiSuggestion
const parseReport = (json: string) => JSON.parse(json) as AiReport
const parseChatResponse = (json: string) => JSON.parse(json) as AiChatResponse

export const runLogAnalyzeTask = async (logId: number): Promise<AiSuggestion> =>
  runTask(await createLogAnalyzeTask(logId), parseSuggestion)

export const runLogRefineTask = async (logId: number, message: string): Promise<AiChatResponse> =>
  runTask(await createLogRefineTask(logId, message), parseChatResponse)

export const runDailySummaryTask = async (date: string): Promise<AiReport> =>
  runTask(await createDailySummaryTask(date), parseReport)

export const runPeriodReportTask = async (
  startDate: string,
  endDate: string,
  title: string,
): Promise<AiReport> => runTask(await createPeriodReportTask(startDate, endDate, title), parseReport)

export const runReportRefineTask = async (
  reportId: number,
  message: string,
): Promise<AiChatResponse> =>
  runTask(await createReportRefineTask(reportId, message), parseChatResponse)

export const runXiaojiChatTask = async (
  message: string,
  sessionId?: number,
): Promise<AiChatResponse> =>
  runTask(await createXiaojiChatTask(message, sessionId), parseChatResponse)

export { AiTaskFailedError }
