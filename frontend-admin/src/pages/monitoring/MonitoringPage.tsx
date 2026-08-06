import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent, type KeyboardEvent } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router'
import { reportTypeLabels } from '../../mocks/adminMockData'
import type { AdminReport, RiskLevel, WebRTCConnection, WebRTCConnectionStatus } from '../../types/admin'
import { RiskBadge } from '../../components/admin/RiskBadge'
import { getRiskConfig, matchesRiskLevel, RISK_LEVEL_OPTIONS } from '../../utils/adminRisk'
import { ROUTE_PATHS, createMonitoringDetailPath, createReportDetailPath } from '../../router/paths'
import { useAuth } from '../../auth/useAuth'
import { getReportMessages, sendAdminLiveMessage, type ReportMessage } from '../../services/adminLiveMessage'
import { subscribeToRealtimeSignals, type RealtimeEventType } from '../../api/realtime'
import {
  detailToAdminReport,
  getDashboardReports,
  getOperatorReportDetail,
  getReportAnalysis,
  type OperatorReportDetail,
  type RecordingStatus,
  type ReportAnalysis,
} from '../reports/reportApi'
import { ReportActionPanel } from '../reports/ReportActionPanel'
import { formatAdminDate } from '../../utils/adminDate'
import { getSubcategoryLabel } from '../../utils/reportLabels'
import { LiveViewer } from './LiveViewer'

const allValue = 'all'
const unsetValue = ''
const gridOptions = [2, 3, 5] as const

type MediaTypeFilter = 'image' | 'live' | typeof allValue

interface MonitoringFilters {
  // 위험도와 같은 방식이다. 빈 값은 '아직 고르지 않음'이라 select 에 '제보 유형'이 보이고,
  // 필터로는 전체와 똑같이 취급한다. 전체는 드롭다운 안에 그대로 남는다.
  mediaType: MediaTypeFilter | typeof unsetValue
  riskLevel: RiskLevel | typeof allValue | typeof unsetValue
}

interface MonitoringNotice {
  id: number
  tone: 'info' | 'warning' | 'success'
  message: string
}

const initialFilters: MonitoringFilters = {
  mediaType: unsetValue,
  riskLevel: unsetValue,
}

function LocationIcon() {
  return (
    <svg className="monitoring-filter__location-icon" viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false">
      <path d="M20 10c0 5-8 11-8 11S4 15 4 10a8 8 0 1 1 16 0Z" />
      <circle cx="12" cy="10" r="2.5" />
    </svg>
  )
}

function ClearIcon() {
  return (
    <svg className="monitoring-filter__clear-icon" viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false">
      <path d="m6 6 12 12M18 6 6 18" />
    </svg>
  )
}

function DetailInfoIcon() {
  return (
    <svg className="report-card__icon" viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false">
      <rect x="4" y="3" width="16" height="18" rx="2" />
      <path d="M8 8h8M8 12h8M8 16h5" />
    </svg>
  )
}

function HandlingIcon() {
  return (
    <svg className="report-card__icon" viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false">
      <circle cx="9" cy="8" r="3.2" />
      <path d="M3.5 19a5.5 5.5 0 0 1 11 0" />
      <path d="M16.5 6.5a3 3 0 0 1 0 5.6M18 19a5.6 5.6 0 0 0-2.2-4.4" />
    </svg>
  )
}

function PinIcon() {
  return (
    <svg className="monitoring-report-card__pin" viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false">
      <path d="M12 21s7-5.7 7-11a7 7 0 1 0-14 0c0 5.3 7 11 7 11Z" />
      <circle cx="12" cy="10" r="2.6" />
    </svg>
  )
}

const realtimeNoticeMessages: Partial<Record<RealtimeEventType, { tone: MonitoringNotice['tone']; text: string }>> = {
  REPORT_ASSIGNED: { tone: 'info', text: '새 제보가 배정되었습니다.' },
  IMAGE_READY: { tone: 'success', text: '제보 이미지 업로드가 완료되었습니다.' },
  LIVE_READY: { tone: 'info', text: '라이브 룸이 준비되었습니다.' },
  LIVE_ACTIVE: { tone: 'success', text: '제보자의 라이브 송출이 시작되었습니다.' },
  LIVE_DISCONNECTED: { tone: 'warning', text: '제보자의 라이브 연결이 끊어졌습니다. 재연결을 기다립니다.' },
  LIVE_RECONNECTED: { tone: 'success', text: '제보자의 라이브 연결이 복구되었습니다.' },
  LIVE_ENDED: { tone: 'warning', text: '제보자가 라이브 송출을 종료했습니다.' },
  REPORT_CANCELLED: { tone: 'warning', text: '제보자가 제보를 취소했습니다.' },
}

// 원본 녹화 URL 이 없을 때 왜 볼 수 없는지 알려준다. COMPLETED 인데 URL 이 없는 경우는
// 파일이 아직 스토리지에 올라오지 않은 상태다.
const recordingHints: Record<RecordingStatus, string> = {
  STARTING: '원본 영상을 준비하는 중입니다.',
  ACTIVE: '원본 영상을 준비하는 중입니다.',
  COMPLETED: '원본 영상을 아직 불러올 수 없습니다.',
  FAILED: '원본 영상이 저장되지 않았습니다.',
  ABORTED: '원본 영상이 저장되지 않았습니다.',
}

function toLiveConnection(report: AdminReport): WebRTCConnection | null {
  if (!report.isLive || !report.streamRoomId) return null

  const connectionStatus: WebRTCConnectionStatus = report.liveStatus === 'LIVE'
    ? 'live'
    : report.liveStatus === 'DISCONNECTED'
      ? 'waiting'
      : report.liveStatus === 'ENDED'
        ? 'ended'
        : 'requested'

  return {
    id: report.streamRoomId,
    reportId: report.id,
    location: report.location,
    riskLevel: report.riskLevel,
    riskScore: report.riskScore,
    connectedAt: report.reportedAt,
    connectionStatus,
    quality: null,
    aiDetected: false,
    aiAnalysisResult: report.aiSummary,
    confidence: null,
  }
}

export function MonitoringPage() {
  const navigate = useNavigate()
  const { connectionId } = useParams()
  const [searchParams, setSearchParams] = useSearchParams()
  const keywordFromUrl = searchParams.get('keyword')?.trim() ?? ''
  const [regionInput, setRegionInput] = useState(keywordFromUrl)
  const [filters, setFilters] = useState<MonitoringFilters>(initialFilters)
  const [columns, setColumns] = useState<(typeof gridOptions)[number]>(2)
  const [reports, setReports] = useState<AdminReport[]>([])
  // 대시보드 타일에는 위험도가 없어 제보별 AI 분석 결과를 따로 모은다. 카드 테두리 색의 근거다.
  const [analyses, setAnalyses] = useState<Record<number, ReportAnalysis>>({})
  const requestedAnalysisIds = useRef(new Set<number>())
  const [notice, setNotice] = useState<MonitoringNotice | null>(null)
  const [loadError, setLoadError] = useState('')
  const [isLoading, setIsLoading] = useState(true)

  const loadReports = useCallback(async () => {
    try {
      const loaded = await getDashboardReports(0, 100)
      setReports(loaded)
      setLoadError('')
    } catch (error) {
      setLoadError(error instanceof Error ? error.message : '관제 제보를 불러오지 못했습니다.')
    } finally {
      setIsLoading(false)
    }
  }, [])

  const loadAnalysis = useCallback(async (reportId: number) => {
    requestedAnalysisIds.current.add(reportId)
    try {
      const analysis = await getReportAnalysis(reportId)
      setAnalyses((current) => ({ ...current, [reportId]: analysis }))
    } catch {
      // 분석 결과가 아직 없어도 관제 카드는 그대로 보여준다.
    }
  }, [])

  // 새로 나타난 제보의 분석 결과만 추가 조회한다. 이미 받은 결과는
  // RISK_ASSESSMENT_UPDATED 이벤트가 올 때만 다시 가져온다.
  useEffect(() => {
    reports
      .map((report) => report.id)
      .filter((reportId) => !requestedAnalysisIds.current.has(reportId))
      .forEach((reportId) => void loadAnalysis(reportId))
  }, [loadAnalysis, reports])

  useEffect(() => {
    const initialLoad = window.setTimeout(() => void loadReports(), 0)
    const unsubscribe = subscribeToRealtimeSignals((signal) => {
      if (signal.kind === 'connected') {
        void loadReports()
        return
      }

      const { event } = signal
      if (event.reportId == null) return
      const reportId = event.reportId
      const noticeConfig = realtimeNoticeMessages[event.eventType]
      if (noticeConfig) {
        setNotice({
          id: event.eventId,
          tone: noticeConfig.tone,
          message: `제보 #${reportId} · ${noticeConfig.text}`,
        })
      }

      if (event.eventType === 'RISK_ASSESSMENT_UPDATED') void loadAnalysis(reportId)

      if (event.eventType !== 'REPORT_MESSAGE') void loadReports()
    })
    return () => {
      window.clearTimeout(initialLoad)
      unsubscribe()
    }
  }, [loadAnalysis, loadReports])

  const selectedReport = connectionId
    ? reports.find((report) => report.streamRoomId === connectionId)
    : undefined
  const selectedConnection = selectedReport ? toLiveConnection(selectedReport) : null

  const applyRegion = (region: string) => {
    setSearchParams((current) => {
      const next = new URLSearchParams(current)
      const keyword = region.trim()
      if (keyword) next.set('keyword', keyword)
      else next.delete('keyword')
      return next
    }, { replace: true })
  }

  const clearRegion = () => {
    setRegionInput('')
    applyRegion('')
  }

  const resetFilters = () => {
    setFilters(initialFilters)
    clearRegion()
  }

  const filteredReports = useMemo(
    () => reports.filter((report) => {
      const analysis = analyses[report.id]
      const matchesRegion = keywordFromUrl.length === 0
        || report.location.toLowerCase().includes(keywordFromUrl.toLowerCase())
      const matchesType = !filters.mediaType || filters.mediaType === allValue
        || (filters.mediaType === 'live' ? report.isLive : !report.isLive)
      const matchesRisk = !filters.riskLevel || filters.riskLevel === allValue
        || matchesRiskLevel(
          analysis?.riskLevel ?? report.riskLevel,
          filters.riskLevel,
          analysis?.riskScore ?? report.riskScore,
        )
      return matchesRegion && matchesType && matchesRisk
    }),
    [analyses, filters, keywordFromUrl, reports],
  )

  const openReport = (report: AdminReport) => {
    const connection = toLiveConnection(report)
    if (connection) navigate(createMonitoringDetailPath(connection.id))
    else navigate(createReportDetailPath(report.id))
  }

  const completeLiveAction = () => {
    void loadReports().finally(() => navigate(ROUTE_PATHS.MONITORING, { replace: true }))
  }

  return (
    <section className="page-stack">
      {notice && (
        <aside className={`monitoring-notice monitoring-notice--${notice.tone}`} role="status" aria-live="polite">
          <span>{notice.message}</span>
          <button type="button" className="text-button" onClick={() => setNotice(null)}>확인</button>
        </aside>
      )}

      {connectionId && isLoading ? (
        <section className="admin-card"><p role="status">라이브 상세를 불러오는 중입니다...</p></section>
      ) : connectionId && selectedReport && selectedConnection ? (
        <MonitoringDetailView
          connection={selectedConnection}
          report={selectedReport}
          analysis={analyses[selectedReport.id] ?? null}
          onBack={() => navigate(ROUTE_PATHS.MONITORING)}
          onActionComplete={completeLiveAction}
        />
      ) : connectionId ? (
        <section className="admin-card empty-state">
          <strong>해당 실시간 영상을 찾을 수 없습니다.</strong>
          <button type="button" className="primary-button" onClick={() => navigate(ROUTE_PATHS.MONITORING)}>통합 관제로 이동</button>
        </section>
      ) : (
        <>
          <div className="monitoring-filter">
            <div className="monitoring-filter__controls">
              <form className="monitoring-filter__region" role="search" onSubmit={(event) => { event.preventDefault(); applyRegion(regionInput) }}>
                <LocationIcon />
                <input
                  aria-label="지역 검색"
                  type="search"
                  value={regionInput}
                  onChange={(event) => setRegionInput(event.target.value)}
                  placeholder="지역명을 입력하세요."
                />
                {regionInput && (
                  <button type="button" className="monitoring-filter__clear" onClick={clearRegion} aria-label="지역 검색어 초기화" title="검색어 지우기">
                    <ClearIcon />
                  </button>
                )}
              </form>
                <select
                  aria-label="제보 유형 선택"
                  className={!filters.mediaType ? 'select-placeholder' : undefined}
                  value={filters.mediaType}
                  onChange={(event) => setFilters((current) => ({
                    ...current,
                    mediaType: event.target.value as MonitoringFilters['mediaType'],
                  }))}
                >
                  <option value={unsetValue} disabled>제보 유형</option>
                  <option value={allValue}>전체</option>
                  <option value="image">이미지</option>
                  <option value="live">라이브</option>
                </select>
                <select
                  aria-label="위험도 선택"
                  className={!filters.riskLevel ? 'select-placeholder' : undefined}
                  value={filters.riskLevel}
                  onChange={(event) => setFilters((current) => ({
                    ...current,
                    riskLevel: event.target.value as MonitoringFilters['riskLevel'],
                  }))}
                >
                  <option value={unsetValue} disabled>위험도</option>
                  <option value={allValue}>전체</option>
                  {RISK_LEVEL_OPTIONS.map(({ value, label }) => (
                    <option key={value} value={value}>{label}</option>
                  ))}
                </select>
              <button type="button" className="secondary-button" onClick={resetFilters}>초기화</button>
            </div>
            <div className="segmented-control monitoring-grid-selector" aria-label="화면 배치 선택">
              {gridOptions.map((option) => (
                <button
                  key={option}
                  type="button"
                  className={columns === option ? 'active' : undefined}
                  onClick={() => setColumns(option)}
                >
                  {option}x{option}
                </button>
              ))}
            </div>
          </div>

          {isLoading ? (
            <article className="admin-card"><p role="status">관제 제보를 불러오는 중입니다...</p></article>
          ) : loadError ? (
            <article className="admin-card"><p className="empty-state" role="alert">{loadError}</p></article>
          ) : (
            <MonitoringGrid
              columns={columns}
              reports={filteredReports}
              analyses={analyses}
              onSelect={openReport}
            />
          )}
        </>
      )}
    </section>
  )
}

interface MonitoringGridProps {
  columns: (typeof gridOptions)[number]
  reports: AdminReport[]
  analyses: Record<number, ReportAnalysis>
  onSelect: (report: AdminReport) => void
}

function MonitoringGrid({ columns, reports, analyses, onSelect }: MonitoringGridProps) {
  return (
    <article className="admin-card monitoring-grid-card">
      {reports.length > 0 ? (
        <div className={`monitoring-grid columns-${columns}`}>
          {reports.map((report) => (
            <MonitoringReportCard
              key={report.id}
              report={report}
              analysis={analyses[report.id] ?? null}
              onSelect={() => onSelect(report)}
            />
          ))}
        </div>
      ) : (
        <p className="empty-state">조건에 맞는 관제 제보가 없습니다.</p>
      )}
    </article>
  )
}

interface MonitoringReportCardProps {
  report: AdminReport
  analysis: ReportAnalysis | null
  onSelect: () => void
}

function MonitoringReportCard({ report, analysis, onSelect }: MonitoringReportCardProps) {
  // 이미지 URL 은 presigned 라 만료될 수 있다. 실패하면 깨진 아이콘 대신 안내로 바꿔
  // 프레임 크기를 그대로 유지한다.
  const [imageFailed, setImageFailed] = useState(false)
  const connection = toLiveConnection(report)
  const isEnded = connection?.connectionStatus === 'ended'
  // AI 위험도에 따라 카드 테두리 색을 입혀 관제사가 위험 제보를 한눈에 찾게 한다.
  const riskLevel = analysis?.riskLevel ?? report.riskLevel
  const riskScore = analysis?.riskScore ?? report.riskScore
  const riskConfig = getRiskConfig(riskLevel, riskScore)

  return (
    <button
      type="button"
      className={`video-card monitoring-report-card${riskConfig ? ' monitoring-report-card--risk' : ''}`}
      style={riskConfig ? { borderColor: riskConfig.markerColor } : undefined}
      onClick={onSelect}
    >
      {/* 미디어 프레임이 유형 배지의 기준 요소다. 배지를 프레임 밖(카드 직계 자식)에 두면
          이미지 크기·위치에 따라 배지가 프레임에서 떨어져 보인다. 라이브·이미지·대기 상태가
          모두 이 프레임 하나를 쓰므로 카드 골격이 유형에 따라 갈라지지 않는다. */}
      <span className="video-card__stream">
        <span className={`media-type-badge ${report.isLive ? 'media-type-badge--live' : 'media-type-badge--image'}`}>
          {report.isLive ? 'LIVE' : 'IMAGE'}
        </span>
        {report.isLive ? (
          !connection ? (
            <span className="video-card__status">라이브 룸 준비 중...</span>
          ) : isEnded ? (
            <span className="video-card__status">라이브 종료됨</span>
          ) : (
            <LiveViewer connection={connection} compact />
          )
        ) : report.imageUrl && !imageFailed ? (
          <img
            className="video-card__video"
            src={report.imageUrl}
            alt={`제보 #${report.id} 대표 이미지`}
            loading="lazy"
            decoding="async"
            onError={() => setImageFailed(true)}
          />
        ) : (
          <span className="video-card__status">
            {report.imageUrl ? '이미지를 불러오지 못했습니다.' : '이미지 업로드 대기 중...'}
          </span>
        )}
      </span>
      <span className="monitoring-report-card__info">
        <strong>
          <PinIcon />
          {report.location || '위치 정보 없음'}
        </strong>
        <RiskBadge value={riskLevel} score={riskScore} />
        <span>제보 #{report.id} · {reportTypeLabels[report.type]}</span>
        <span className="monitoring-report-card__description">{report.aiSummary || '상세 내용 없음'}</span>
      </span>
    </button>
  )
}

interface MonitoringDetailViewProps {
  connection: WebRTCConnection
  report: AdminReport
  analysis: ReportAnalysis | null
  onBack: () => void
  onActionComplete: () => void
}

function MonitoringDetailView({ connection, report, analysis, onBack, onActionComplete }: MonitoringDetailViewProps) {
  const { adminName } = useAuth()
  const [reportDetail, setReportDetail] = useState<OperatorReportDetail | null>(null)
  const [detailError, setDetailError] = useState('')
  const [message, setMessage] = useState('')
  const [messageError, setMessageError] = useState('')
  const [isSending, setIsSending] = useState(false)
  const [messages, setMessages] = useState<ReportMessage[]>([])
  // 라이브가 끝난 뒤 같은 프레임에서 원본 녹화를 재생한다. presigned URL 은 만료될 수 있어
  // 재생 실패도 프레임 안에서 안내하고 종료 화면으로 되돌린다.
  const [isRecordingOpen, setIsRecordingOpen] = useState(false)
  const [recordingError, setRecordingError] = useState('')

  useEffect(() => {
    let active = true
    void getOperatorReportDetail(connection.reportId)
      .then((detail) => {
        if (active) setReportDetail(detail)
      })
      .catch((error: unknown) => {
        if (active) setDetailError(error instanceof Error ? error.message : '제보 상세를 불러오지 못했습니다.')
      })
    return () => { active = false }
  }, [connection.reportId])

  useEffect(() => {
    let active = true
    const loadMessages = async () => {
      try {
        const loaded = await getReportMessages(connection.reportId)
        if (active) setMessages(loaded.filter((item) => item.senderRole !== 'CITIZEN'))
      } catch {
        // 영상 연결과 메시지 이력 조회는 독립적으로 유지한다.
      }
    }
    void loadMessages()
    const unsubscribe = subscribeToRealtimeSignals((signal) => {
      if (signal.kind === 'connected'
        || (signal.event.eventType === 'REPORT_MESSAGE' && signal.event.reportId === connection.reportId)) {
        void loadMessages()
      }
    })
    return () => {
      active = false
      unsubscribe()
    }
  }, [connection.reportId])

  const sendMessage = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (isSending) return

    const trimmedMessage = message.trim()
    if (!trimmedMessage) {
      setMessageError('전달할 메시지를 입력해주세요.')
      return
    }
    if (connection.connectionStatus !== 'live') {
      setMessageError('현재 연결된 세션에서만 메시지를 보낼 수 있습니다.')
      return
    }
    if (!adminName) {
      setMessageError('관리자 정보를 확인할 수 없습니다.')
      return
    }

    setIsSending(true)
    setMessageError('')
    try {
      const sent = await sendAdminLiveMessage({ reportId: connection.reportId, message: trimmedMessage })
      setMessages((current) => [...current, sent])
      setMessage('')
    } catch (error) {
      setMessageError(error instanceof Error ? error.message : '메시지 전송에 실패했습니다.')
    } finally {
      setIsSending(false)
    }
  }

  const preventImeSubmit = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Enter' && event.nativeEvent.isComposing) event.preventDefault()
  }

  const detailReport = reportDetail ? detailToAdminReport(reportDetail) : report
  const recordingUrl = reportDetail?.originalVideoUrl ?? null
  const recordingHint = recordingUrl || !reportDetail?.recordingStatus
    ? ''
    : recordingHints[reportDetail.recordingStatus]

  return (
    <>
      <button type="button" className="secondary-button" onClick={onBack}>← 통합 관제로 돌아가기</button>

      <div className="monitoring-detail-layout">
        <article className="admin-card monitoring-featured-video">
          <div className="featured-video-frame">
            {connection.connectionStatus === 'live' && <span className="live-badge">LIVE</span>}
            {connection.connectionStatus !== 'ended' ? (
              <LiveViewer connection={connection} />
            ) : isRecordingOpen && recordingUrl ? (
              <>
                <video
                  className="featured-live-video featured-recording-video"
                  src={recordingUrl}
                  controls
                  autoPlay
                  playsInline
                  onError={() => {
                    setIsRecordingOpen(false)
                    setRecordingError('원본 영상을 재생하지 못했습니다. 잠시 후 다시 시도해주세요.')
                  }}
                />
                <button type="button" className="featured-recording-close" onClick={() => setIsRecordingOpen(false)}>
                  영상 닫기
                </button>
              </>
            ) : (
              <div className="featured-live-ended">
                <span>라이브 송출이 종료되었습니다.</span>
                {recordingUrl && (
                  <button
                    type="button"
                    className="featured-recording-link"
                    onClick={() => {
                      setRecordingError('')
                      setIsRecordingOpen(true)
                    }}
                  >
                    원본 영상 보기
                  </button>
                )}
                {recordingHint && <span className="featured-recording-hint">{recordingHint}</span>}
                {recordingError && <span className="featured-recording-hint" role="alert">{recordingError}</span>}
              </div>
            )}
          </div>
          <form className="live-message-form" onSubmit={sendMessage}>
            <label className="sr-only" htmlFor={`live-message-${connection.id}`}>현장 사용자에게 보낼 메시지</label>
            <input
              id={`live-message-${connection.id}`}
              type="text"
              maxLength={1000}
              value={message}
              onChange={(event) => {
                setMessage(event.target.value)
                if (messageError) setMessageError('')
              }}
              onKeyDown={preventImeSubmit}
              placeholder="예) 화면을 더 높이 들어주세요."
              aria-describedby={messageError ? 'live-message-error' : undefined}
            />
            <button type="submit" className="primary-button" disabled={isSending || connection.connectionStatus !== 'live'}>
              {isSending ? '전송 중...' : '전송'}
            </button>
          </form>
          {messageError && <p id="live-message-error" className="live-message-error" role="alert">{messageError}</p>}
          <section className="live-message-history" aria-live="polite" aria-label="제보자에게 보낸 메시지">
            <h3>제보자에게 보낸 메시지</h3>
            {messages.length ? messages.map((item) => (
              <article key={item.messageId}>
                <p>{item.content}</p>
                <time dateTime={item.sentAt}>{new Date(item.sentAt).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })}</time>
              </article>
            )) : <p className="empty-state">아직 보낸 메시지가 없습니다.</p>}
          </section>
        </article>

        <div className="monitoring-detail-sidebar">
          {/* 제목을 카드 안에 두어 사진 제보 상세(ReportDetailPage)와 같은 골격을 쓴다. */}
          <article className="admin-card report-card monitor-detail">
            <h2 className="report-card__title"><DetailInfoIcon />제보 상세 정보</h2>
            {detailError && <p className="login-error" role="alert">{detailError}</p>}
            <dl className="monitor-detail__list">
              <div className="monitor-detail__row"><dt>제보 ID</dt><dd>#{connection.reportId}</dd></div>
              {/* 카테고리와 세부 유형은 한 값으로 묶고, 짧은 위험도와 한 줄을 나눠 쓴다. */}
              <div>
                <dt>카테고리</dt>
                <dd>{[reportTypeLabels[detailReport.type], getSubcategoryLabel(reportDetail?.subcategory)]
                  .filter(Boolean)
                  .join(' · ')}</dd>
              </div>
              <div>
                <dt>위험도</dt>
                <dd>
                  <RiskBadge
                    value={analysis?.riskLevel ?? connection.riskLevel}
                    score={analysis?.riskScore ?? connection.riskScore}
                  />
                </dd>
              </div>
              <div className="monitor-detail__row"><dt>제보 시간</dt><dd>{formatAdminDate(detailReport.reportedAt)}</dd></div>
              <div className="monitor-detail__row"><dt>위치</dt><dd>{detailReport.location}</dd></div>
              {/* 'AI 분석 결과' 항목을 뺐다. 이 화면은 라이브(WebRTC) 제보 전용이고,
                  값으로 쓰던 connection.aiAnalysisResult 는 toLiveConnection 에서
                  report.aiSummary(제보자가 쓴 제보 내용)를 그대로 넣은 것이라 아래 '제보 내용'
                  과 같은 문장이 두 번 보였다. 위험도는 위 항목이 이미 보여준다.
                  WebRTCConnection.aiAnalysisResult 필드와 API 는 그대로 두고 표시만 뺀다.
                  사진 제보 상세(ReportDetailPage)의 분석 표시는 건드리지 않았다. */}
              <div className="monitor-detail__row monitor-detail__description">
                <dt>제보 내용</dt>
                <dd className="break-text">{reportDetail?.description ?? detailReport.aiSummary}</dd>
              </div>
            </dl>
          </article>

          <article className="admin-card report-card">
            <h2 className="report-card__title"><HandlingIcon />제보 처리</h2>
            <ReportActionPanel
              reportId={connection.reportId}
              assignmentStatus={reportDetail?.myAssignmentStatus ?? null}
              onActionComplete={onActionComplete}
            />
          </article>
        </div>
      </div>
    </>
  )
}
