import { AUTH_TOKEN_KEY } from '../constants/app'

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api'

function stripMarkdownFence(text: string): string {
  const trimmed = text.trim()
  if (!trimmed.startsWith('```')) return trimmed
  return trimmed
    .replace(/^```(?:json)?\s*/, '')
    .replace(/\s*```$/, '')
    .trim()
}

/**
 * 流式请求，累计所有 chunk，done 事件触发后 JSON.parse 并返回结果。
 * 适用于 refine 类接口，AI 输出是 JSON，需要完整接收后才能解析。
 */
export async function streamFetchJson<T>(
  path: string,
  body: unknown,
  onChunk?: (chunk: string) => void,
): Promise<T> {
  const token = localStorage.getItem(AUTH_TOKEN_KEY)

  const response = await fetch(`${BASE_URL}${path}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(body),
  })

  if (!response.ok) {
    throw new Error(`Stream request failed: ${response.status}`)
  }

  const reader = response.body!.getReader()
  const decoder = new TextDecoder()
  let sseBuffer = ''
  let accumulated = ''
  let currentEvent = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    sseBuffer += decoder.decode(value, { stream: true })
    const lines = sseBuffer.split('\n')
    sseBuffer = lines.pop() ?? ''

    for (const line of lines) {
      if (line.startsWith('event:')) {
        currentEvent = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        const data = line.slice(5).trim()
        if (!data) continue

        if (currentEvent === 'done') {
          if (data && data.trim() !== '') {
            return JSON.parse(data) as T
          }
          return JSON.parse(stripMarkdownFence(accumulated)) as T
        } else {
          accumulated += data
          onChunk?.(data)
        }
        currentEvent = ''
      } else if (line === '') {
        currentEvent = ''
      }
    }
  }

  return JSON.parse(stripMarkdownFence(accumulated)) as T
}

export async function streamFetch(
  path: string,
  body: unknown,
  onChunk: (chunk: string) => void,
  onSessionId?: (sessionId: number) => void,
): Promise<void> {
  const token = localStorage.getItem(AUTH_TOKEN_KEY)

  const response = await fetch(`${BASE_URL}${path}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(body),
  })

  if (!response.ok) {
    throw new Error(`Stream request failed: ${response.status}`)
  }

  const reader = response.body!.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let currentEvent = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() ?? ''

    for (const line of lines) {
      if (line.startsWith('event:')) {
        currentEvent = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        const data = line.slice(5).trim()
        if (!data) continue

        if (currentEvent === 'done') {
          return
        } else if (currentEvent === 'session') {
          try {
            const parsed = JSON.parse(data)
            if (parsed.sessionId !== undefined) onSessionId?.(parsed.sessionId)
          } catch {
            // Ignore malformed session payloads and keep the stream alive.
          }
        } else {
          onChunk(data)
        }
        currentEvent = ''
      } else if (line === '') {
        currentEvent = ''
      }
    }
  }
}
