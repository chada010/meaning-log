import http from './http'

export interface MeaningLog {
  id: number
  title: string | null
  content: string
  logDate: string
  mood?: string | null
  aiTitle?: string | null
  aiSummary?: string | null
  aiTags?: string | null
  favorite: boolean
  images: LogImage[]
  createdAt: string
  updatedAt: string
}

export interface LogImageRequest {
  fileName?: string
  caption?: string
  contentType?: string
  dataUrl: string
}

export interface LogImage extends LogImageRequest {
  id?: number
  contentType: string
  fileSize?: number
  url?: string
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
  title?: string
  content: string
  logDate: string
  mood?: string
  favorite?: boolean
  images?: LogImageRequest[]
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

export const getLogAiChat = (id: number) => {
  return http.get<AiChatResponse>(`/logs/${id}/ai/chat`)
}

export const applyLogAi = (id: number, data: AiSuggestion) => {
  return http.post<MeaningLog>(`/logs/${id}/ai/apply`, data)
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

export const getXiaojiSessions = () => {
  return http.get<AiChatSession[]>('/xiaoji/sessions')
}

export const getXiaojiMessages = (sessionId: number) => {
  return http.get<AiChatMessage[]>(`/xiaoji/sessions/${sessionId}/messages`)
}

