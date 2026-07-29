import { clearAccessToken, getAccessToken } from './accessToken'
import { apiBaseUrl, refreshSession } from './client'
import { USE_MOCK_API } from './mockMode'
import { notifySessionExpired } from './sessionEvents'

export type RealtimeEventType =
  | 'REPORT_ASSIGNED'
  | 'IMAGE_READY'
  | 'LIVE_READY'
  | 'LIVE_ACTIVE'
  | 'LIVE_DISCONNECTED'
  | 'LIVE_RECONNECTED'
  | 'LIVE_ENDED'
  | 'REPORT_CLOSED'
  | 'REPORT_REJECTED'
  | 'REPORT_CANCELLED'
  | 'REPORT_REASSIGNED'
  | 'REPORT_MESSAGE'

export interface RealtimeEvent {
  eventId: number
  eventType: RealtimeEventType
  reportId: number
  resourceId: string | null
  messageId: number | null
  createdAt: string
}

export type RealtimeSignal =
  | { kind: 'connected' }
  | { kind: 'event'; event: RealtimeEvent }

const signalTarget = new EventTarget()
const signalName = 'safy-realtime'
const reconnectDelay = 3000

export function subscribeToRealtimeSignals(listener: (signal: RealtimeSignal) => void) {
  const handler = (event: Event) => listener((event as CustomEvent<RealtimeSignal>).detail)
  signalTarget.addEventListener(signalName, handler)
  return () => signalTarget.removeEventListener(signalName, handler)
}

function publishSignal(signal: RealtimeSignal) {
  signalTarget.dispatchEvent(new CustomEvent<RealtimeSignal>(signalName, { detail: signal }))
}

function parseEventBlock(block: string): { id: string | null; data: string } | null {
  if (!block || block.startsWith(':')) return null

  let id: string | null = null
  const dataLines: string[] = []
  for (const line of block.split(/\r?\n/)) {
    if (line.startsWith('id:')) id = line.slice(3).trim()
    if (line.startsWith('data:')) dataLines.push(line.slice(5).trimStart())
  }
  return dataLines.length ? { id, data: dataLines.join('\n') } : null
}

function realtimeUrl() {
  return apiBaseUrl.replace(/\/$/, '') + '/realtime/events'
}

async function waitForReconnect(signal: AbortSignal) {
  await new Promise<void>((resolve) => {
    const timer = window.setTimeout(resolve, reconnectDelay)
    signal.addEventListener('abort', () => {
      window.clearTimeout(timer)
      resolve()
    }, { once: true })
  })
}

async function consumeEventStream(signal: AbortSignal, cursor: { value: string | null }) {
  const token = getAccessToken() ?? await refreshSession()
  const url = realtimeUrl()
  const headers = new Headers({
    Accept: 'text/event-stream',
    Authorization: 'Bearer ' + token,
  })

  if (cursor.value && new URL(url, window.location.origin).origin === window.location.origin) {
    headers.set('Last-Event-ID', cursor.value)
  }

  const response = await fetch(url, {
    method: 'GET',
    headers,
    credentials: 'include',
    signal,
  })
  if (response.status === 401) {
    try {
      await refreshSession()
    } catch (error) {
      clearAccessToken()
      notifySessionExpired()
      throw error
    }
    throw new Error('SSE access token refreshed')
  }
  if (!response.ok || !response.body) {
    throw new Error('SSE connection failed: ' + response.status)
  }

  publishSignal({ kind: 'connected' })
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (!signal.aborted) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n')

    let boundary = buffer.indexOf('\n\n')
    while (boundary >= 0) {
      const parsed = parseEventBlock(buffer.slice(0, boundary))
      buffer = buffer.slice(boundary + 2)
      if (parsed) {
        if (parsed.id) cursor.value = parsed.id
        try {
          const event = JSON.parse(parsed.data) as RealtimeEvent
          if (event.eventType && event.reportId) publishSignal({ kind: 'event', event })
        } catch {
          // CONNECTED 이벤트처럼 제보 이벤트가 아닌 데이터는 무시한다.
        }
      }
      boundary = buffer.indexOf('\n\n')
    }
  }
}

export function startRealtimeEvents() {
  // 목 모드에서는 SSE 서버가 없어 3초마다 재연결만 반복하므로 아예 시작하지 않는다.
  if (USE_MOCK_API) return () => {}

  const controller = new AbortController()
  const cursor = { value: null as string | null }

  const run = async () => {
    while (!controller.signal.aborted) {
      try {
        await consumeEventStream(controller.signal, cursor)
      } catch (error) {
        if (controller.signal.aborted || (error instanceof DOMException && error.name === 'AbortError')) return
      }
      if (!controller.signal.aborted) await waitForReconnect(controller.signal)
    }
  }

  void run()
  return () => controller.abort()
}
