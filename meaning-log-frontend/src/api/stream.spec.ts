import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { StreamFetchError, streamFetch, streamFetchJson } from './stream'

const encoder = new TextEncoder()

function streamResponse(chunks: string[]): Response {
  return new Response(new ReadableStream({
    start(controller) {
      for (const chunk of chunks) controller.enqueue(encoder.encode(chunk))
      controller.close()
    },
  }))
}

function failingResponse(error: Error): Response {
  return new Response(new ReadableStream({
    start(controller) {
      controller.error(error)
    },
  }))
}

function mockResponse(response: Response): void {
  globalThis.fetch = async () => response
}

afterEach(() => {
  globalThis.fetch = originalFetch
  if (originalLocalStorage) {
    Object.defineProperty(globalThis, 'localStorage', originalLocalStorage)
  } else {
    delete (globalThis as { localStorage?: Storage }).localStorage
  }
})

const originalFetch = globalThis.fetch
const originalLocalStorage = Object.getOwnPropertyDescriptor(globalThis, 'localStorage')

beforeEach(() => {
  Object.defineProperty(globalThis, 'localStorage', {
    configurable: true,
    value: { getItem: () => null },
  })
})

describe('streamFetch', () => {
  it('保留 data 分片中的前后空白，并在 done 后成功', async () => {
    mockResponse(streamResponse(['data:  leading and trailing  \n\nevent: done\n\n']))
    const chunks: string[] = []

    await streamFetch('/chat', {}, (chunk) => chunks.push(chunk))

    expect(chunks).toEqual([' leading and trailing  '])
  })

  it('按事件边界合并多条 data 行并保留换行', async () => {
    mockResponse(streamResponse(['data: first line\ndata: second line\n\nevent: done\n\n']))
    const chunks: string[] = []

    await streamFetch('/chat', {}, (chunk) => chunks.push(chunk))

    expect(chunks).toEqual(['first line\nsecond line'])
  })

  it('处理 session 事件后继续读取并在 done 后成功', async () => {
    mockResponse(streamResponse(['event: session\ndata: {"sessionId":42}\n\ndata: reply\n\nevent: done\n\n']))
    const sessionIds: number[] = []
    const chunks: string[] = []

    await streamFetch('/chat', {}, (chunk) => chunks.push(chunk), (sessionId) => sessionIds.push(sessionId))

    expect(sessionIds).toEqual([42])
    expect(chunks).toEqual(['reply'])
  })

  it('在未收到 done 即断流时抛出可识别错误', async () => {
    mockResponse(streamResponse(['data: partial\n\n']))

    await expect(streamFetch('/chat', {}, () => undefined)).rejects.toSatisfy(isStreamError('incomplete'))
  })

  it('将连接失败包装为可识别错误', async () => {
    globalThis.fetch = async () => {
      throw new Error('network unavailable')
    }

    await expect(streamFetch('/chat', {}, () => undefined)).rejects.toSatisfy(isStreamError('connection'))
  })

  it('将读取失败包装为可识别错误', async () => {
    mockResponse(failingResponse(new Error('connection reset')))

    await expect(streamFetch('/chat', {}, () => undefined)).rejects.toSatisfy(isStreamError('read'))
  })
})

describe('streamFetchJson', () => {
  it('仅在收到 done 后解析并返回 JSON 结果', async () => {
    mockResponse(streamResponse(['data: {"title":"draft"}\n\nevent: done\n\n']))

    const result = await streamFetchJson<{ title: string }>('/refine', {})

    expect(result).toEqual({ title: 'draft' })
  })

  it('在未收到 done 即断流时抛出可识别错误', async () => {
    mockResponse(streamResponse(['data: {"title":"draft"}\n\n']))

    await expect(streamFetchJson('/refine', {})).rejects.toSatisfy(isStreamError('incomplete'))
  })
})

function isStreamError(code: StreamFetchError['code']): (error: unknown) => boolean {
  return (error: unknown) => error instanceof StreamFetchError && error.code === code
}
