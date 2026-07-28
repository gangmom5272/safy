import { useCallback, useEffect, useMemo, useState, type FormEvent, type KeyboardEvent } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router'
import {
  connectionQualityLabels,
  connectionStatusLabels,
  reportTypeLabels,
} from '../../mocks/adminMockData'
import type { AdminReport, RiskLevel, WebRTCConnection, WebRTCConnectionStatus } from '../../types/admin'
import { AdminSection } from '../../components/admin/AdminSection'
import { RiskBadge } from '../../components/admin/RiskBadge'
import { getRiskLabel, matchesRiskLevel, RISK_LEVEL_OPTIONS } from '../../utils/adminRisk'
import { ROUTE_PATHS, createMonitoringDetailPath, createReportDetailPath } from '../../router/paths'
import { useAuth } from '../../auth/useAuth'
import { getReportMessages, sendAdminLiveMessage, type ReportMessage } from '../../services/adminLiveMessage'
import { subscribeToRealtimeSignals, type RealtimeEventType } from '../../api/realtime'
import {
  detailToAdminReport,
  getDashboardReports,
  getOperatorReportDetail,
  type OperatorReportDetail,
} from '../reports/reportApi'
import { ReportActionPanel } from '../reports/ReportActionPanel'
import { formatAdminDate } from '../../utils/adminDate'
import { LiveViewer } from './LiveViewer'

const allValue = 'all'
const unsetValue = ''
const gridOptions = [2, 3, 5] as const
const dismissedEndedReportsKey = 'safy-admin-dismissed-ended-reports'

type MediaTypeFilter = 'image' | 'live' | typeof allValue

interface MonitoringFilters {
  mediaType: MediaTypeFilter
  riskLevel: RiskLevel | typeof allValue | typeof unsetValue
  connectionStatus: WebRTCConnectionStatus | typeof allValue | typeof unsetValue
}

interface MonitoringNotice {
  id: number
  tone: 'info' | 'warning' | 'success'
  message: string
}

const initialFilters: MonitoringFilters = {
  mediaType: allValue,
  riskLevel: unsetValue,
  connectionStatus: unsetValue,
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

function readDismissedEndedReports() {
  try {
    const parsed = JSON.parse(window.localStorage.getItem(dismissedEndedReportsKey) ?? '[]') as unknown
    return new Set(Array.isArray(parsed) ? parsed.filter((value): value is number => Number.isInteger(value)) : [])
  } catch {
    return new Set<number>()
  }
}

function persistDismissedEndedReports(reportIds: Set<number>) {
  window.localStorage.setItem(dismissedEndedReportsKey, JSON.stringify([...reportIds]))
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
  const [dismissedEndedReports, setDismissedEndedReports] = useState(readDismissedEndedReports)
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

  useEffect(() => {
    const initialLoad = window.setTimeout(() => void loadReports(), 0)
    const unsubscribe = subscribeToRealtimeSignals((signal) => {
      if (signal.kind === 'connected') {
        void loadReports()
        return
      }

      const { event } = signal
      const noticeConfig = realtimeNoticeMessages[event.eventType]
      if (noticeConfig) {
        setNotice({
          id: event.eventId,
          tone: noticeConfig.tone,
          message: `제보 #${event.reportId} · ${noticeConfig.text}`,
        })
      }

      if (event.eventType === 'LIVE_READY'
        || event.eventType === 'LIVE_ACTIVE'
        || event.eventType === 'LIVE_RECONNECTED') {
        setDismissedEndedReports((current) => {
          if (!current.has(event.reportId)) return current
          const next = new Set(current)
          next.delete(event.reportId)
          persistDismissedEndedReports(next)
          return next
        })
      }

      if (event.eventType !== 'REPORT_MESSAGE') void loadReports()
    })
    return () => {
      window.clearTimeout(initialLoad)
      unsubscribe()
    }
  }, [loadReports])

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

  const dismissEndedReport = (reportId: number) => {
    setDismissedEndedReports((current) => {
      const next = new Set(current)
      next.add(reportId)
      persistDismissedEndedReports(next)
      return next
    })
  }

  const filteredReports = useMemo(
    () => reports.filter((report) => {
      const connection = toLiveConnection(report)
      if (connection?.connectionStatus === 'ended' && dismissedEndedReports.has(report.id)) return false

      const matchesRegion = keywordFromUrl.length === 0
        || report.location.toLowerCase().includes(keywordFromUrl.toLowerCase())
      const matchesType = filters.mediaType === allValue
        || (filters.mediaType === 'live' ? report.isLive : !report.isLive)
      const matchesRisk = !filters.riskLevel || filters.riskLevel === allValue
        || matchesRiskLevel(report.riskLevel, filters.riskLevel, report.riskScore)
      const matchesStatus = !filters.connectionStatus || filters.connectionStatus === allValue
        || connection?.connectionStatus === filters.connectionStatus

      return matchesRegion && matchesType && matchesRisk && matchesStatus
    }),
    [dismissedEndedReports, filters, keywordFromUrl, reports],
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
                  aria-label="제보 방식 선택"
                  value={filters.mediaType}
                  onChange={(event) => setFilters((current) => ({
                    ...current,
                    mediaType: event.target.value as MediaTypeFilter,
                  }))}
                >
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
                <select
                  aria-label="연결 상태 선택"
                  className={!filters.connectionStatus ? 'select-placeholder' : undefined}
                  value={filters.connectionStatus}
                  onChange={(event) => setFilters((current) => ({
                    ...current,
                    connectionStatus: event.target.value as MonitoringFilters['connectionStatus'],
                  }))}
                >
                  <option value={unsetValue} disabled>연결 상태</option>
                  <option value={allValue}>전체</option>
                  {Object.entries(connectionStatusLabels).map(([value, label]) => (
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
              onSelect={openReport}
              onDismissEnded={dismissEndedReport}
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
  onSelect: (report: AdminReport) => void
  onDismissEnded: (reportId: number) => void
}

function MonitoringGrid({ columns, reports, onSelect, onDismissEnded }: MonitoringGridProps) {
  return (
    <article className="admin-card monitoring-grid-card">
      {reports.length > 0 ? (
        <div className={`monitoring-grid columns-${columns}`}>
          {reports.map((report) => (
            <MonitoringReportCard
              key={report.id}
              report={report}
              onSelect={() => onSelect(report)}
              onDismissEnded={() => onDismissEnded(report.id)}
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
  onSelect: () => void
  onDismissEnded: () => void
}

function MonitoringReportCard({ report, onSelect, onDismissEnded }: MonitoringReportCardProps) {
  const connection = toLiveConnection(report)
  const isEnded = connection?.connectionStatus === 'ended'

  return (
    <div className="monitoring-tile">
      <button type="button" className="video-card monitoring-report-card" onClick={onSelect}>
        <span className={`media-type-badge ${report.isLive ? 'media-type-badge--live' : 'media-type-badge--image'}`}>
          {report.isLive ? 'LIVE' : 'IMAGE'}
        </span>
        {connection?.connectionStatus === 'live' && <span className="live-badge">LIVE</span>}
        <span className="video-card__stream">
          {report.isLive ? (
            !connection ? (
              <span className="video-card__status">라이브 룸 준비 중...</span>
            ) : isEnded ? (
              <span className="video-card__status">라이브 종료됨</span>
            ) : (
              <LiveViewer connection={connection} compact />
            )
          ) : report.imageUrl ? (
            <img className="video-card__video" src={report.imageUrl} alt={`제보 #${report.id} 대표 이미지`} />
          ) : (
            <span className="video-card__status">이미지 업로드 대기 중...</span>
          )}
        </span>
        <strong>{report.location || '위치 정보 없음'}</strong>
        <RiskBadge value={report.riskLevel} score={report.riskScore} />
        <span>{report.isLive
          ? `상태 ${connection ? connectionStatusLabels[connection.connectionStatus] : '준비 중'}`
          : `첨부 이미지 ${report.imageCount ?? 0}장`}</span>
        <span>{reportTypeLabels[report.type]} · 제보 #{report.id}</span>
        <span className="monitoring-report-card__description">{report.aiSummary || '상세 내용 없음'}</span>
      </button>
      {isEnded && (
        <button type="button" className="monitoring-tile__dismiss" onClick={onDismissEnded}>
          종료 확인 · 관제에서 숨기기
        </button>
      )}
    </div>
  )
}

interface MonitoringDetailViewProps {
  connection: WebRTCConnection
  report: AdminReport
  onBack: () => void
  onActionComplete: () => void
}

function MonitoringDetailView({ connection, report, onBack, onActionComplete }: MonitoringDetailViewProps) {
  const { adminName } = useAuth()
  const [reportDetail, setReportDetail] = useState<OperatorReportDetail | null>(null)
  const [detailError, setDetailError] = useState('')
  const [message, setMessage] = useState('')
  const [messageError, setMessageError] = useState('')
  const [isSending, setIsSending] = useState(false)
  const [messages, setMessages] = useState<ReportMessage[]>([])

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

  return (
    <>
      <button type="button" className="secondary-button" onClick={onBack}>← 통합 관제로 돌아가기</button>

      <div className="monitoring-detail-layout">
        <article className="admin-card monitoring-featured-video">
          <div className="featured-video-frame">
            {connection.connectionStatus === 'live' && <span className="live-badge">LIVE</span>}
            {connection.connectionStatus === 'ended'
              ? <span className="featured-live-status">라이브 송출이 종료되었습니다.</span>
              : <LiveViewer connection={connection} />}
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
          <aside className="admin-card monitor-detail">
            <h2>라이브 제보 상세 정보</h2>
            {detailError && <p className="login-error" role="alert">{detailError}</p>}
            <dl>
              <div><dt>제보 ID</dt><dd>#{connection.reportId}</dd></div>
              <div><dt>카테고리</dt><dd>{reportTypeLabels[detailReport.type]}</dd></div>
              <div><dt>세부 유형</dt><dd>{reportDetail?.subcategory ?? '정보 없음'}</dd></div>
              <div><dt>제보 내용</dt><dd className="break-text">{reportDetail?.description ?? detailReport.aiSummary}</dd></div>
              <div><dt>위치</dt><dd>{detailReport.location}</dd></div>
              <div><dt>제보 시간</dt><dd>{formatAdminDate(detailReport.reportedAt)}</dd></div>
              <div><dt>관제센터</dt><dd>{detailReport.controlCenterName ?? '정보 없음'}</dd></div>
              <div><dt>연결 상태</dt><dd>{connectionStatusLabels[connection.connectionStatus]}</dd></div>
              <div><dt>연결 품질</dt><dd>{connection.quality ? connectionQualityLabels[connection.quality] : '정보 없음'}</dd></div>
              <div><dt>위험도</dt><dd>{getRiskLabel(connection.riskLevel, connection.riskScore)}</dd></div>
              <div><dt>AI 분석 결과</dt><dd className="break-text">{connection.aiAnalysisResult || '정보 없음'}</dd></div>
            </dl>
          </aside>

          <AdminSection title="제보 처리">
            <ReportActionPanel reportId={connection.reportId} onActionComplete={onActionComplete} />
          </AdminSection>
        </div>
      </div>
    </>
  )
}
