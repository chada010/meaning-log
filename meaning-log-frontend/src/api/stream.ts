import { AUTH_TOKEN_KEY } from '../constants/app'

const BASE_URL = import.meta.env?.VITE_API_BASE_URL ?? 'http://localhost:8080/api'

type StreamFetchErrorCode = 'connection' | 'http' | 'read' | 'incomplete'

export class StreamFetchError extends Error {
  readonly code: StreamFetchErrorCode

  constructor(
    code: StreamFetchErrorCode,
    message: string,
    options?: ErrorOptions,
  ) {
    super(message, options)
    this.code = code
    this.name = 'StreamFetchError'
  }
}

function stripMarkdownFence(text: string): string {
  const trimmed = text.trim()
  if (!trimmed.startsWith('```')) return trimmed
  return trimmed
    .replace(/^```(?:json)?\s*/, '')
    .replace(/\s*```$/, '')
    .trim()
}

async function requestStream(path: string, body: unknown): Promise<ReadableStreamDefaultReader<Uint8Array>> {
  const token = localStorage.getItem(AUTH_TOKEN_KEY)
  let response: Response

  try {
    response = await fetch(`${BASE_URL}${path}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify(body),
    })
  } catch (error) {
    throw new StreamFetchError('connection', 'Unable to connect to stream endpoint', { cause: error })
  }

  if (!response.ok) {
    throw new StreamFetchError('http', `Stream request failed: ${response.status}`)
  }
  if (!response.body) {
    throw new StreamFetchError('read', 'Stream response has no body')
  }
  return response.body.getReader()
}

function dataFromLine(line: string): string {
  const data = line.slice(5)
  return data.startsWith(' ') ? data.slice(1) : data
}

async function readSse(
  reader: ReadableStreamDefaultReader<Uint8Array>,
  onData: (event: string, data: string) => boolean,
): Promise<void> {
  const decoder = new TextDecoder()
  let buffer = ''
  let currentEvent = ''

  const processLine = (rawLine: string): boolean => {
    const line = rawLine.endsWith('\r') ? rawLine.slice(0, -1) : rawLine
    if (line.startsWith('event:')) {
      currentEvent = line.slice(6).trim()
      return false
    }
    if (line.startsWith('data:')) {
      return onData(currentEvent, dataFromLine(line))
    }
    if (line === '') {
      currentEvent = ''
    }
    return false
  }

  const processDecoded = (): boolean => {
    const lines = buffer.split('\n')
    buffer = lines.pop() ?? ''
    return lines.some(processLine)
  }

  while (true) {
    let readResult: ReadableStreamReadResult<Uint8Array>
    try {
      readResult = await reader.read()
    } catch (error) {
      throw new StreamFetchError('read', 'Unable to read stream response', { cause: error })
    }

    if (readResult.done) break
    buffer += decoder.decode(readResult.value, { stream: true })
    if (processDecoded()) return
  }

  buffer += decoder.decode()
  if (processDecoded() || (buffer !== '' && processLine(buffer))) return
  throw new StreamFetchError('incomplete', 'SSE stream ended before done event')
}

/**
 * 流式请求，累计所有 chunk，收到 done 事件后解析并返回结果。
 * 适用于 refine 类接口，AI 输出是 JSON，需要完整接收后才能解析。
 */
export async function streamFetchJson<T>(
  path: string,
  body: unknown,
  onChunk?: (chunk: string) => void,
): Promise<T> {
  const reader = await requestStream(path, body)
  let accumulated = ''
  let result: T | undefined

  await readSse(reader, (event, data) => {
    if (event === 'done') {
      result = JSON.parse(data || stripMarkdownFence(accumulated)) as T
      return true
    }
    accumulated += data
    onChunk?.(data)
    return false
  })

  return result as T
}

export async function streamFetch(
  path: string,
  body: unknown,
  onChunk: (chunk: string) => void,
  onSessionId?: (sessionId: number) => void,
): Promise<void> {
  const reader = await requestStream(path, body)

  await readSse(reader, (event, data) => {
    if (event === 'done') return true
    if (event === 'session') {
      try {
        const parsed = JSON.parse(data) as { sessionId?: number }
        if (parsed.sessionId !== undefined) onSessionId?.(parsed.sessionId)
      } catch {
        // Ignore malformed session payloads and keep the stream alive.
      }
      return false
    }
    onChunk(data)
    return false
  })
}
