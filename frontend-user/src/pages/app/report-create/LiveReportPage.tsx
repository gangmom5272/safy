import { useCallback, useEffect, useRef, useState } from 'react'
import { Room, RoomEvent, Track } from 'livekit-client'
import { ConfirmReportModal } from './ConfirmReportModal'
import {
  DEFAULT_REPORT_CATEGORY,
  DEFAULT_REPORT_METHOD,
  REPORT_CATEGORY_LABELS,
} from './reportConstants'
import type { ReportFormData } from './reportTypes'
import { createReport } from './reportApi'
import { createLiveSession, endLiveSession, issueReporterToken } from './liveApi'
import { buildCreateReportRequest } from './reportMapper'
import { getCitizenReportMessages, type ReportMessage } from './reportMessageApi'
import { subscribeToRealtimeSignals } from '../../../api/realtime'
import { USE_MOCK_API } from '../../../api/mockMode'
import './report-create.css'

type CameraStatus =
  | 'idle'
  | 'connecting'
  | 'ready'
  | 'denied'
  | 'not-found'
  | 'not-readable'
  | 'overconstrained'
  | 'unsupported'
  | 'security'
  | 'aborted'
  | 'error'

interface LiveReportPageProps {
  formData?: ReportFormData
  onBack?: () => void
  onComplete?: (formData: ReportFormData) => void
}

const fallbackFormData: ReportFormData = {
  method: DEFAULT_REPORT_METHOD,
  category: DEFAULT_REPORT_CATEGORY,
  selectedTags: [],
  content: '',
  location: null,
}

function formatElapsedTime(seconds: number): string {
  const minutes = Math.floor(seconds / 60)
  const remainingSeconds = seconds % 60

  return `${String(minutes).padStart(2, '0')}:${String(remainingSeconds).padStart(2, '0')}`
}

function getCameraErrorStatus(error: unknown): CameraStatus {
  if (!(error instanceof DOMException)) {
    return 'error'
  }

  if (error.name === 'NotAllowedError' || error.name === 'PermissionDeniedError') {
    return 'denied'
  }

  if (error.name === 'NotFoundError' || error.name === 'DevicesNotFoundError') {
    return 'not-found'
  }

  if (error.name === 'NotReadableError') {
    return 'not-readable'
  }

  if (error.name === 'OverconstrainedError') {
    return 'overconstrained'
  }

  if (error.name === 'SecurityError') {
    return 'security'
  }

  if (error.name === 'AbortError') {
    return 'aborted'
  }

  return 'error'
}

function getCameraMessage(status: CameraStatus): string {
  switch (status) {
    case 'idle':
      return '카메라 연결 전입니다.'
    case 'connecting':
      return '카메라와 마이크를 연결하는 중입니다.'
    case 'ready':
      return '카메라 미리보기 준비 완료'
    case 'denied':
      return '카메라 또는 마이크 권한이 거부되었습니다. 브라우저 설정에서 권한을 허용해주세요.'
    case 'not-found':
      return '사용 가능한 카메라 또는 마이크 장치를 찾지 못했습니다.'
    case 'not-readable':
      return '다른 앱에서 카메라를 사용 중이거나 장치를 열 수 없습니다.'
    case 'overconstrained':
      return '요청한 카메라 조건을 사용할 수 없습니다.'
    case 'unsupported':
      return '이 브라우저는 카메라 접근 기능을 지원하지 않습니다.'
    case 'security':
      return '보안 환경 문제로 카메라를 사용할 수 없습니다. localhost 또는 HTTPS에서 다시 시도해주세요.'
    case 'aborted':
      return '카메라 연결이 중단되었습니다. 다시 시도해주세요.'
    case 'error':
      return '카메라 연결 중 알 수 없는 오류가 발생했습니다.'
  }
}

async function requestPreferredMediaStream(): Promise<MediaStream> {
  try {
    return await navigator.mediaDevices.getUserMedia({
      video: {
        facingMode: { ideal: 'environment' },
      },
      audio: false,
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'OverconstrainedError') {
      return navigator.mediaDevices.getUserMedia({ video: true, audio: false })
    }

    throw error
  }
}

export function LiveReportPage({
  formData = fallbackFormData,
  onBack,
  onComplete,
}: LiveReportPageProps) {
  const videoRef = useRef<HTMLVideoElement | null>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const roomRef = useRef<Room | null>(null)
  const requestInProgressRef = useRef(false)
  const mountedRef = useRef(false)
  const timerRef = useRef<number | null>(null)
  const submitInProgressRef = useRef(false)
  // 라이브 제보는 시작 시점에 제보와 스트림 룸을 만들고 종료 시점에 세션을 닫는다.
  const liveSessionRef = useRef<{ reportId: number; streamRoomId: string } | null>(null)
  // 담당자 처리(종료·반려)로 끝났는지. LiveKit 의 일반 종료 메시지가 사유를 덮지 않도록 한다.
  const endedByOperatorRef = useRef(false)
  const [cameraStatus, setCameraStatus] = useState<CameraStatus>('idle')
  const [isStarting, setIsStarting] = useState(false)
  const [isReporting, setIsReporting] = useState(false)
  const [elapsedSeconds, setElapsedSeconds] = useState(0)
  const [isConfirmOpen, setIsConfirmOpen] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState('')
  const [activeReportId, setActiveReportId] = useState<number | null>(null)
  const [hasReconnectableSession, setHasReconnectableSession] = useState(false)
  const [operatorMessages, setOperatorMessages] = useState<ReportMessage[]>([])
  // 담당자 처리 결과(종료·반려·재배정) 안내. 오류가 아니라 상태 통지라 크게 따로 보여준다.
  const [operatorNotice, setOperatorNotice] = useState('')

  const stopTimer = useCallback(() => {
    if (timerRef.current !== null) {
      window.clearInterval(timerRef.current)
      timerRef.current = null
    }
  }, [])

  const cleanupCamera = useCallback((resetRequestFlag = true) => {
    roomRef.current?.disconnect()
    roomRef.current = null
    if (streamRef.current) {
      streamRef.current.getTracks().forEach((track) => track.stop())
      streamRef.current = null
    }

    if (videoRef.current) {
      videoRef.current.srcObject = null
    }

    if (resetRequestFlag) {
      requestInProgressRef.current = false
    }
  }, [])

  const startCamera = useCallback(async () => {
    if (requestInProgressRef.current || streamRef.current) {
      return
    }

    if (!navigator.mediaDevices?.getUserMedia) {
      setCameraStatus('unsupported')
      return
    }

    requestInProgressRef.current = true
    setCameraStatus('connecting')

    try {
      const stream = await requestPreferredMediaStream()
      requestInProgressRef.current = false

      if (!mountedRef.current) {
        stream.getTracks().forEach((track) => track.stop())
        return
      }

      streamRef.current = stream

      if (videoRef.current) {
        videoRef.current.srcObject = stream
      }

      setCameraStatus('ready')
    } catch (error) {
      requestInProgressRef.current = false

      if (!mountedRef.current) {
        return
      }

      cleanupCamera()
      setCameraStatus(getCameraErrorStatus(error))
    }
  }, [cleanupCamera])

  const startLiveReport = useCallback(async () => {
    if (cameraStatus !== 'ready' || isReporting || isStarting) {
      return
    }

    setIsStarting(true)
    setOperatorMessages([])
    setSubmitError('')
    setOperatorNotice('')
    endedByOperatorRef.current = false

    let createdSession: { streamRoomId: string } | null = null
    try {
      const existingSession = liveSessionRef.current
      let reportId: number
      let session: { streamRoomId: string }

      if (existingSession) {
        reportId = existingSession.reportId
        session = { streamRoomId: existingSession.streamRoomId }
      } else {
        const report = await createReport(buildCreateReportRequest({ ...formData, method: 'live' }))
        reportId = report.reportId
        session = await createLiveSession(report.reportId)
        createdSession = session
      }

      const token = await issueReporterToken(session.streamRoomId)

      // 목 모드에는 LiveKit 서버가 없어 실제 송출은 건너뛰고 로컬 미리보기만 유지한다.
      if (!USE_MOCK_API) {
        const room = new Room({ adaptiveStream: true, dynacast: true })
        room.on(RoomEvent.Reconnecting, () => {
          if (mountedRef.current) setSubmitError('라이브 연결을 복구하고 있습니다...')
        })
        room.on(RoomEvent.Reconnected, () => {
          if (mountedRef.current) setSubmitError('')
        })
        room.on(RoomEvent.Disconnected, () => {
          if (!mountedRef.current) return
          // 담당자 처리로 끝난 경우엔 SSE 핸들러가 이미 정리하고 사유를 표시했다.
          // disconnect() 가 비동기로 이 이벤트를 발생시키므로 여기서 덮어쓰면 사유가 사라진다.
          if (endedByOperatorRef.current) return
          roomRef.current = null
          if (streamRef.current) {
            streamRef.current.getTracks().forEach((track) => track.stop())
            streamRef.current = null
          }
          if (videoRef.current) videoRef.current.srcObject = null
          stopTimer()
          setIsReporting(false)
          setActiveReportId(null)
          setCameraStatus('idle')
          setSubmitError('라이브 연결이 종료되었습니다.')
        })
        await room.connect(token.serverUrl, token.token)
        const videoTrack = streamRef.current?.getVideoTracks()[0]
        if (!videoTrack) throw new Error('송출할 카메라 영상을 찾을 수 없습니다.')
        await room.localParticipant.publishTrack(videoTrack, { source: Track.Source.Camera })
        roomRef.current = room
      }

      if (!mountedRef.current) return

      liveSessionRef.current = { reportId, streamRoomId: session.streamRoomId }
      setHasReconnectableSession(true)
      setActiveReportId(reportId)
      setIsReporting(true)
      setElapsedSeconds(0)
      stopTimer()
      timerRef.current = window.setInterval(() => {
        setElapsedSeconds((seconds) => seconds + 1)
      }, 1000)
    } catch (error) {
      roomRef.current?.disconnect()
      roomRef.current = null
      if (createdSession) void endLiveSession(createdSession.streamRoomId).catch(() => undefined)
      if (mountedRef.current) {
        setSubmitError(error instanceof Error ? error.message : '라이브 제보를 시작하지 못했습니다.')
      }
    } finally {
      if (mountedRef.current) setIsStarting(false)
    }
  }, [cameraStatus, formData, isReporting, isStarting, stopTimer])

  useEffect(() => {
    if (!activeReportId) return

    let active = true
    const loadMessages = async () => {
      try {
        const messages = await getCitizenReportMessages(activeReportId)
        if (active) setOperatorMessages(messages.filter((message) => message.senderRole !== 'CITIZEN'))
      } catch {
        // 송출은 메시지 조회 실패와 독립적으로 유지한다.
      }
    }
    void loadMessages()
    const unsubscribe = subscribeToRealtimeSignals((signal) => {
      if (signal.kind === 'connected'
        || (signal.event.eventType === 'REPORT_MESSAGE' && signal.event.reportId === activeReportId)) {
        void loadMessages()
      }
    })
    return () => {
      active = false
      unsubscribe()
    }
  }, [activeReportId])

  useEffect(() => subscribeToRealtimeSignals((signal) => {
    if (signal.kind !== 'event') return
    const session = liveSessionRef.current
    if (!session || signal.event.reportId !== session.reportId) return

    const { eventType } = signal.event

    // 재배정은 담당자만 바뀌고 송출은 그대로 이어진다. 안내만 하고 세션을 건드리지 않는다.
    if (eventType === 'REPORT_REASSIGNED') {
      setOperatorNotice('담당자가 변경되었습니다.\n라이브는 계속 진행됩니다.')
      return
    }

    if (eventType !== 'LIVE_ENDED' && eventType !== 'REPORT_CLOSED' && eventType !== 'REPORT_REJECTED') return

    endedByOperatorRef.current = true
    stopTimer()
    setIsReporting(false)
    setIsConfirmOpen(false)
    setActiveReportId(null)
    cleanupCamera()
    liveSessionRef.current = null
    setHasReconnectableSession(false)

    if (eventType === 'REPORT_CLOSED') {
      onComplete?.({ ...formData, method: 'live', reportId: session.reportId })
      return
    }
    setCameraStatus('idle')
    setOperatorNotice(eventType === 'REPORT_REJECTED'
      ? '담당자가 제보를 반려했습니다.\n라이브가 종료되었습니다.'
      : '담당자가 제보를 처리했습니다.\n라이브가 종료되었습니다.')
  }), [cleanupCamera, formData, onComplete, stopTimer])

  const requestFinishLiveReport = () => {
    if (!isReporting || isConfirmOpen) {
      return
    }

    setIsConfirmOpen(true)
  }

  const confirmFinishLiveReport = useCallback(async () => {
    if (submitInProgressRef.current) {
      return
    }

    const session = liveSessionRef.current

    submitInProgressRef.current = true
    setIsSubmitting(true)
    stopTimer()
    setIsReporting(false)
    cleanupCamera()
    setActiveReportId(null)
    setSubmitError('')
    try {
      if (session) await endLiveSession(session.streamRoomId)
      liveSessionRef.current = null
      setHasReconnectableSession(false)
      onComplete?.({ ...formData, method: 'live', reportId: session?.reportId })
    } catch (error) {
      submitInProgressRef.current = false
      setIsSubmitting(false)
      setIsConfirmOpen(false)
      setSubmitError(error instanceof Error ? error.message : '제보를 종료하지 못했습니다.')
    }
  }, [cleanupCamera, formData, onComplete, stopTimer])

  const closeConfirmModal = useCallback(() => {
    if (isSubmitting) {
      return
    }

    setIsConfirmOpen(false)
  }, [isSubmitting])

  const handleBack = () => {
    const session = liveSessionRef.current
    liveSessionRef.current = null
    setHasReconnectableSession(false)
    if (session) void endLiveSession(session.streamRoomId).catch(() => undefined)
    setIsConfirmOpen(false)
    stopTimer()
    setIsReporting(false)
    setElapsedSeconds(0)
    cleanupCamera()
    setActiveReportId(null)
    onBack?.()
  }

  const handleRetry = () => {
    stopTimer()
    setIsReporting(false)
    setElapsedSeconds(0)
    cleanupCamera()
    setActiveReportId(null)
    startCamera()
  }

  useEffect(() => {
    mountedRef.current = true
    const startTimer = window.setTimeout(startCamera, 0)

    return () => {
      window.clearTimeout(startTimer)
      mountedRef.current = false
      stopTimer()
      cleanupCamera(false)
    }
  }, [cleanupCamera, startCamera, stopTimer])

  const locationText = formData.location ? '현재 위치' : '위치 정보 없음'
  const categoryLabel = formData.category ? REPORT_CATEGORY_LABELS[formData.category] : '선택 없음'
  const cameraMessage = getCameraMessage(cameraStatus)
  const canStart = cameraStatus === 'ready' && !isReporting && !isStarting

  return (
    <section className="report-live-overlay" aria-label="실시간 영상 제보">
      <div className="report-live-topbar">
        <button type="button" className="report-live-back" onClick={handleBack} aria-label="제보 방식 선택으로 돌아가기">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m15 5-7 7 7 7"/><path d="M8 12h12"/></svg>
        </button>
        <div className="report-live-status" aria-live="polite">
          {isReporting ? <span className="report-live-badge">제보 중 {formatElapsedTime(elapsedSeconds)}</span> : null}
          <span className={`report-info-badge report-info-badge-${formData.category ?? 'none'}`}>{categoryLabel}</span>
        </div>
      </div>

      <div className="report-live-stage">
        <video ref={videoRef} className="report-live-video" autoPlay playsInline muted />
        {cameraStatus !== 'ready' ? (
          <div className="report-live-panel" role={cameraStatus === 'connecting' ? 'status' : 'alert'}>
            <h1>실시간 영상 제보</h1>
            <p>{cameraMessage}</p>
            {cameraStatus !== 'connecting' ? (
              <>
                <button type="button" className="report-primary-button" onClick={handleRetry}>
                  다시 시도
                </button>
                <button type="button" className="report-ghost-button" onClick={handleBack}>
                  제보 방식 선택으로 돌아가기
                </button>
              </>
            ) : null}
          </div>
        ) : null}
      </div>

      <div className="report-live-bottom">
        {operatorNotice ? (
          <p className="report-live-notice" role="alert">{operatorNotice}</p>
        ) : null}
        {submitError ? <p className="report-error-text" role="alert">{submitError}</p> : null}
        {isReporting && operatorMessages.length ? (
          <section className="report-live-messages" aria-live="polite" aria-label="담당자 안내 메시지">
            <strong>담당자 안내</strong>
            {operatorMessages.slice(-3).map((message) => (
              <p key={message.messageId}>{message.content}</p>
            ))}
          </section>
        ) : null}
        {/* 송출이 시작되면 사전 안내는 감추고 현장 화면과 담당자 안내에 집중시킨다. */}
        {!isReporting ? (
          <>
            <ul className="report-live-summary" aria-label="제보 정보">
              <li>
                <span><LiveInfoIcon name="location" />{locationText}</span>
              </li>
              <li>
                <span><LiveInfoIcon name="warning" />{categoryLabel}</span>
              </li>
            </ul>
            <ul className="report-live-safety" aria-label="안전 안내">
              <li><LiveInfoIcon name="camera" />현장 상황만 촬영해주세요.</li>
              <li><LiveInfoIcon name="camera" />타인의 얼굴이 노출되지 않도록 주의해주세요.</li>
              <li><LiveInfoIcon name="shield" />안전한 장소에서 제보해주세요.</li>
            </ul>
          </>
        ) : null}
        <button
          type="button"
          className={`report-live-control ${canStart ? 'ready' : ''}`}
          disabled={cameraStatus !== 'ready' || isSubmitting || isStarting}
          onClick={isReporting ? requestFinishLiveReport : () => void startLiveReport()}
        >
          {isReporting
            ? '제보 종료'
            : isStarting
              ? '제보 준비 중...'
              : hasReconnectableSession
                ? '라이브 재연결'
                : '실시간 제보 시작'}
        </button>
      </div>

      {isConfirmOpen ? (
        <ConfirmReportModal
          title={`${formData.category ? categoryLabel : '라이브'} 제보를 종료하시겠습니까?`}
          description="라이브 송출이 중단되며 지금까지 전송된 영상으로 제보가 접수됩니다. 종료 후에는 다시 송출할 수 없습니다."
          isSubmitting={isSubmitting}
          cancelLabel="취소"
          confirmLabel="종료하기"
          onCancel={closeConfirmModal}
          onConfirm={confirmFinishLiveReport}
        />
      ) : null}
    </section>
  )
}

function LiveInfoIcon({ name }: { name: 'location' | 'warning' | 'camera' | 'shield' }) {
  const paths = {
    location: <><path d="M20 10c0 5-8 11-8 11S4 15 4 10a8 8 0 1 1 16 0Z" /><circle cx="12" cy="10" r="2.5" /></>,
    warning: <><path d="M12 3 2.8 20h18.4L12 3Z" /><path d="M12 9v4M12 17h.01" /></>,
    camera: <><path d="M8 6 9.5 4h5L16 6h3a2 2 0 0 1 2 2v10H3V8a2 2 0 0 1 2-2h3Z" /><circle cx="12" cy="12" r="3" /></>,
    shield: <><path d="M12 3 20 6v5c0 5-3.4 8.4-8 10-4.6-1.6-8-5-8-10V6l8-3Z" /><path d="m8.5 12 2.2 2.2 4.8-5" /></>,
  }

  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name]}</svg>
}
