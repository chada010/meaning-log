const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api'

export async function streamFetch(
  path: string,
  body: unknown,
  onChunk: (chunk: string) => void,
  onSessionId?: (sessionId: number) => void,
): Promise<void> {
  const token = localStorage.getItem('meaning-log-token')

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
          } catch { /* ignore */ }
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
