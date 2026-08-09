import { useCallback, useEffect, useRef, useState } from 'react'
import type { ReportStatusSocketMessage, WebRtcStatus } from './types'

const MAX_RECONNECT_ATTEMPTS = 3

type SocketConnectionState = 'idle' | 'connecting' | 'open' | 'closed' | 'failed'

type UseReportStatusSocketParams = {
  reportId?: string
  socketUrl?: string
  enabled: boolean
}

export function useReportStatusSocket({
  reportId,
  socketUrl,
  enabled,
}: UseReportStatusSocketParams) {
  const [connectionState, setConnectionState] = useState<SocketConnectionState>('idle')
  const [latestStatus, setLatestStatus] = useState<WebRtcStatus | undefined>()
  const [message, setMessage] = useState('')
  const [reconnectTick, setReconnectTick] = useState(0)
  const socketRef = useRef<WebSocket | null>(null)
  const reconnectAttemptsRef = useRef(0)
  const reconnectTimerRef = useRef<number | null>(null)
  const allowReconnectRef = useRef(true)

  const closeSocket = useCallback(() => {
    allowReconnectRef.current = false

    if (reconnectTimerRef.current) {
      window.clearTimeout(reconnectTimerRef.current)
      reconnectTimerRef.current = null
    }

    socketRef.current?.close()
    socketRef.current = null
  }, [])

  const connect = useCallback(() => {
    if (!enabled || !reportId || !socketUrl || socketRef.current) {
      return
    }

    allowReconnectRef.current = true
    setConnectionState('connecting')
    const socket = new WebSocket(socketUrl)
    socketRef.current = socket

    socket.onopen = () => {
      reconnectAttemptsRef.current = 0
      setConnectionState('open')
    }

    socket.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data) as ReportStatusSocketMessage

        if (data.status) {
          setLatestStatus(data.status)
        }

        if (data.message) {
          setMessage(data.message)
        }
      } catch {
        setMessage('실시간 상태 메시지를 해석하지 못했습니다.')
      }
    }

    socket.onerror = () => {
      setConnectionState('failed')
    }

    socket.onclose = () => {
      socketRef.current = null
      setConnectionState('closed')

      if (!allowReconnectRef.current || reconnectAttemptsRef.current >= MAX_RECONNECT_ATTEMPTS) {
        return
      }

      reconnectAttemptsRef.current += 1
      reconnectTimerRef.current = window.setTimeout(() => {
        setReconnectTick((currentTick) => currentTick + 1)
      }, 1200)
    }
  }, [enabled, reportId, socketUrl])

  useEffect(() => {
    connect()

    return closeSocket
  }, [closeSocket, connect, reconnectTick])

  const reconnect = () => {
    reconnectAttemptsRef.current = 0
    closeSocket()
    window.setTimeout(() => {
      allowReconnectRef.current = true
      setReconnectTick((currentTick) => currentTick + 1)
    }, 0)
  }

  return {
    connectionState,
    latestStatus,
    message,
    reconnect,
    closeSocket,
  }
}
