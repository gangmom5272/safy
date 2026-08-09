import { useEffect, useRef, useState } from 'react'
import { Room, RoomEvent, Track } from 'livekit-client'
import { USE_MOCK_API } from '../../api/mockMode'
import type { WebRTCConnection } from '../../types/admin'
import { issueViewerToken } from './monitoringApi'

interface LiveViewerProps {
  connection: WebRTCConnection
  compact?: boolean
}

export function LiveViewer({ connection, compact = false }: LiveViewerProps) {
  const videoRef = useRef<HTMLVideoElement | null>(null)
  const [status, setStatus] = useState(USE_MOCK_API ? '목 모드 — 영상 없음' : '연결 중...')

  useEffect(() => {
    // 목 모드에서는 LiveKit 서버가 없으므로 자리표시자만 보여준다.
    if (USE_MOCK_API) return

    let active = true
    const room = new Room({ adaptiveStream: true, dynacast: true })
    room.on(RoomEvent.TrackSubscribed, (track) => {
      if (track.kind === Track.Kind.Video && videoRef.current) {
        track.attach(videoRef.current)
        if (active) setStatus('')
      }
    })
    room.on(RoomEvent.TrackUnsubscribed, (track) => {
      if (track.kind === Track.Kind.Video) {
        track.detach()
        if (active) setStatus('제보자 영상 재연결을 기다리는 중...')
      }
    })
    room.on(RoomEvent.Reconnecting, () => {
      if (active) setStatus('영상 연결을 복구하는 중...')
    })
    room.on(RoomEvent.Reconnected, () => {
      if (active) setStatus(videoRef.current?.srcObject ? '' : '영상 수신 대기 중...')
    })
    room.on(RoomEvent.Disconnected, () => {
      if (active) setStatus('영상 연결이 종료되었습니다.')
    })

    void issueViewerToken(String(connection.id))
      .then((token) => room.connect(token.serverUrl, token.token))
      .then(() => {
        if (active) setStatus((current) => current ? '영상 수신 대기 중...' : current)
      })
      .catch((error: unknown) => {
        if (active) setStatus(error instanceof Error ? error.message : '영상에 연결하지 못했습니다.')
      })

    return () => {
      active = false
      room.remoteParticipants.forEach((participant) => {
        participant.trackPublications.forEach((publication) => publication.track?.detach())
      })
      room.disconnect()
    }
  }, [connection.id])

  return (
    <>
      <video ref={videoRef} className={compact ? 'video-card__video' : 'featured-live-video'} autoPlay playsInline muted />
      {status ? <span className={compact ? 'video-card__status' : 'featured-live-status'}>{status}</span> : null}
    </>
  )
}
