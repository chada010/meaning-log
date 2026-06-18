import http from './http'
import { streamFetchJson } from './stream'

export interface MeaningLog {
  id: number
  title: string
  content: string
  logDate: string
  mood?: string
  aiTitle?: string
  aiSummary?: string
  aiTags?: string
  favorite: boolean
  images: LogImage[]
  createdAt: string
  updatedAt: string
}

export interface LogImage {
  id?: number
  fileName?: string
  caption?: string
  contentType: string
  fileSize?: number
  url?: string
  dataUrl: string
}

export interface AiReport {
  id?: number
  type?: 'daily' | 'weekly' | 'monthly' | 'custom'
  title: string
  period: string
  startDate?: string
  endDate?: string
  summary: string
  tags: string
  createdAt?: string
  updatedAt?: string
}

export interface AiSuggestion {
  title: string
  summary: string
  tags: string[]
}

export interface AiChatMessage {
  id: number
  role: 'user' | 'assistant'
  content: string
  createdAt: string
}

export interface AiChatSession {
  id: number
  type: 'general' | 'log' | 'report'
  logId?: number
  reportId?: number
  title: string
  updatedAt: string
}

export interface AiChatResponse {
  sessionId?: number
  reply?: string
  suggestion?: AiSuggestion
  reportSuggestion?: AiReport
  messages: AiChatMessage[]
}

export interface LogNavigation {
  previous?: MeaningLog
  next?: MeaningLog
}

export interface MeaningLogRequest {
  title: string
  content: string
  logDate: string
  mood?: string
  favorite?: boolean
  images?: LogImage[]
}

export interface LogQueryParams {
  date?: string
  keyword?: string
  tag?: string
  favorite?: boolean
}

export const getLogs = (params?: LogQueryParams) => {
  return http.get<MeaningLog[]>('/logs', {
    params,
  })
}

export const getLogDetail = (id: number) => {
  return http.get<MeaningLog>(`/logs/${id}`)
}

export const getLogNavigation = (id: number) => {
  return http.get<LogNavigation>(`/logs/${id}/navigation`)
}

export const getAiTags = () => {
  return http.get<string[]>('/logs/ai/tags')
}

export const createLog = (data: MeaningLogRequest) => {
  return http.post<MeaningLog>('/logs', data)
}

export const updateLog = (id: number, data: MeaningLogRequest) => {
  return http.put<MeaningLog>(`/logs/${id}`, data)
}

export const updateLogFavorite = (id: number, favorite: boolean) => {
  return http.put<MeaningLog>(`/logs/${id}/favorite`, undefined, {
    params: { favorite },
  })
}

export const deleteLog = (id: number) => {
  return http.delete<void>(`/logs/${id}`)
}

export const generateLogAi = (id: number) => {
  return http.post<MeaningLog>(`/logs/${id}/ai`)
}

export const generateLogAiStream = (id: number, onChunk?: (chunk: string) => void) =>
  streamFetchJson<AiSuggestion>(`/logs/${id}/ai/stream`, {}, onChunk)

export const chatWithLogAi = (id: number, message: string) => {
  return http.post<AiChatResponse>(`/logs/${id}/ai/chat`, { message })
}

export const getLogAiChat = (id: number) => {
  return http.get<AiChatResponse>(`/logs/${id}/ai/chat`)
}

export const applyLogAi = (id: number, data: AiSuggestion) => {
  return http.post<MeaningLog>(`/logs/${id}/ai/apply`, data)
}

export const generateDailySummary = (date: string) => {
  return http.post<AiReport>('/logs/ai/daily-summary', undefined, {
    params: { date },
  })
}

export const generateAiReport = (startDate: string, endDate: string, title: string) => {
  return http.post<AiReport>('/logs/ai/report', undefined, {
    params: { startDate, endDate, title },
  })
}

export const getAiReports = () => {
  return http.get<AiReport[]>('/logs/ai/reports')
}

export const getAiReport = (id: number) => {
  return http.get<AiReport>(`/logs/ai/reports/${id}`)
}

export const applyAiReport = (id: number, data: AiReport) => {
  return http.post<AiReport>(`/logs/ai/reports/${id}/apply`, data)
}

export const getAiReportChat = (id: number) => {
  return http.get<AiChatResponse>(`/logs/ai/reports/${id}/chat`)
}

export const chatWithAiReport = (id: number, message: string) => {
  return http.post<AiChatResponse>(`/logs/ai/reports/${id}/chat`, { message })
}

export const chatWithLogAiStream = (id: number, message: string, onChunk?: (chunk: string) => void) =>
  streamFetchJson<AiSuggestion>(`/logs/${id}/ai/chat/stream`, { message }, onChunk)

export const chatWithAiReportStream = (id: number, message: string, onChunk?: (chunk: string) => void) =>
  streamFetchJson<AiReport>(`/logs/ai/reports/${id}/chat/stream`, { message }, onChunk)

export const getXiaojiSessions = () => {
  return http.get<AiChatSession[]>('/xiaoji/sessions')
}

export const getXiaojiMessages = (sessionId: number) => {
  return http.get<AiChatMessage[]>(`/xiaoji/sessions/${sessionId}/messages`)
}

export const chatWithXiaoji = (message: string, sessionId?: number) => {
  return http.post<AiChatResponse>('/xiaoji/chat', { message, sessionId })
}
