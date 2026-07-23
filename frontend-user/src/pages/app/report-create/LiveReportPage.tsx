import { useCallback, useEffect, useRef, useState } from 'react'
import { ConfirmReportModal } from './ConfirmReportModal'
import {
  DEFAULT_REPORT_CATEGORY,
  DEFAULT_REPORT_METHOD,
  REPORT_CATEGORY_LABELS,
} from './reportConstants'
import type { ReportFormData } from './reportTypes'
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

function formatCoordinate(value: number): string {
  return value.toFixed(5)
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
      audio: true,
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'OverconstrainedError') {
      return navigator.mediaDevices.getUserMedia({ video: true, audio: true })
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
  const requestInProgressRef = useRef(false)
  const mountedRef = useRef(false)
  const timerRef = useRef<number | null>(null)
  const submitInProgressRef = useRef(false)
  const [cameraStatus, setCameraStatus] = useState<CameraStatus>('idle')
  const [isReporting, setIsReporting] = useState(false)
  const [elapsedSeconds, setElapsedSeconds] = useState(0)
  const [isConfirmOpen, setIsConfirmOpen] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const stopTimer = useCallback(() => {
    if (timerRef.current !== null) {
      window.clearInterval(timerRef.current)
      timerRef.current = null
    }
  }, [])

  const cleanupCamera = useCallback((resetRequestFlag = true) => {
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

  const startLiveReport = useCallback(() => {
    if (cameraStatus !== 'ready' || isReporting) {
      return
    }

    setIsReporting(true)
    setElapsedSeconds(0)
    stopTimer()
    timerRef.current = window.setInterval(() => {
      setElapsedSeconds((seconds) => seconds + 1)
    }, 1000)
  }, [cameraStatus, isReporting, stopTimer])

  const requestFinishLiveReport = () => {
    if (!isReporting || isConfirmOpen) {
      return
    }

    setIsConfirmOpen(true)
  }

  const confirmFinishLiveReport = useCallback(() => {
    if (submitInProgressRef.current) {
      return
    }

    submitInProgressRef.current = true
    setIsSubmitting(true)
    stopTimer()
    setIsReporting(false)
    cleanupCamera()
    onComplete?.({ ...formData, method: 'live' })
  }, [cleanupCamera, formData, onComplete, stopTimer])

  const closeConfirmModal = useCallback(() => {
    if (isSubmitting) {
      return
    }

    setIsConfirmOpen(false)
  }, [isSubmitting])

  const handleBack = () => {
    setIsConfirmOpen(false)
    stopTimer()
    setIsReporting(false)
    setElapsedSeconds(0)
    cleanupCamera()
    onBack?.()
  }

  const handleRetry = () => {
    stopTimer()
    setIsReporting(false)
    setElapsedSeconds(0)
    cleanupCamera()
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

  const locationText = formData.location
    ? `위도 ${formatCoordinate(formData.location.latitude)}, 경도 ${formatCoordinate(formData.location.longitude)}`
    : '위치 정보 없음'
  const categoryLabel = formData.category ? REPORT_CATEGORY_LABELS[formData.category] : '선택 없음'
  const cameraMessage = getCameraMessage(cameraStatus)
  const canStart = cameraStatus === 'ready' && !isReporting

  return (
    <section className="report-live-overlay" aria-label="실시간 영상 제보">
      <div className="report-live-topbar">
        <button type="button" className="report-live-back" onClick={handleBack} aria-label="제보 방식 선택으로 돌아가기">
          ←
        </button>
        <div className="report-live-status" aria-live="polite">
          {isReporting ? <span className="report-live-badge">제보 중 {formatElapsedTime(elapsedSeconds)}</span> : null}
          <span className="report-info-badge">{categoryLabel}</span>
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
        <ul className="report-live-summary" aria-label="제보 정보">
          <li>
            <span>{locationText}</span>
          </li>
          <li>
            <span>{categoryLabel}</span>
          </li>
        </ul>
        <ul className="report-live-safety" aria-label="안전 안내">
          <li>현장 상황만 촬영해주세요.</li>
          <li>타인의 얼굴이 노출되지 않도록 주의해주세요.</li>
          <li>안전한 장소에서 제보해주세요.</li>
        </ul>
        <button
          type="button"
          className={`report-live-control ${canStart ? 'ready' : ''}`}
          disabled={cameraStatus !== 'ready' || isSubmitting}
          onClick={isReporting ? requestFinishLiveReport : startLiveReport}
        >
          {isReporting ? '실시간 제보 종료' : '실시간 제보 시작'}
        </button>
      </div>

      {isConfirmOpen ? (
        <ConfirmReportModal
          title="정말 이 영상으로 제보하시겠습니까?"
          description="현재 실시간 영상 제보를 종료하고 제출합니다. 허위 또는 장난 제보는 제재 대상이 될 수 있습니다."
          isSubmitting={isSubmitting}
          onCancel={closeConfirmModal}
          onConfirm={confirmFinishLiveReport}
        />
      ) : null}
    </section>
  )
}
