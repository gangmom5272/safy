import { useMemo, useState, type FormEvent, type KeyboardEvent } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router'
import {
  connectionQualityLabels,
  connectionStatusLabels,
  webrtcConnections,
} from '../../mocks/adminMockData'
import type { RiskLevel, WebRTCConnection, WebRTCConnectionStatus } from '../../types/admin'
import { AdminSection } from '../../components/admin/AdminSection'
import { RiskBadge } from '../../components/admin/RiskBadge'
import { getRiskLabel, matchesRiskLevel, RISK_LEVEL_OPTIONS } from '../../utils/adminRisk'
import { ROUTE_PATHS, createMonitoringDetailPath } from '../../router/paths'
import { useAuth } from '../../auth/useAuth'
import { sendAdminLiveMessage } from '../../services/adminLiveMessage'

const allValue = 'all'
const gridOptions = [2, 3, 5] as const

interface MonitoringFilters {
  riskLevel: RiskLevel | typeof allValue
  connectionStatus: WebRTCConnectionStatus | typeof allValue
}

const initialFilters: MonitoringFilters = {
  riskLevel: allValue,
  connectionStatus: allValue,
}

export function MonitoringPage() {
  const navigate = useNavigate()
  const { connectionId } = useParams()
  const [searchParams, setSearchParams] = useSearchParams()
  const keywordFromUrl = searchParams.get('keyword')?.trim() ?? ''
  const [filters, setFilters] = useState<MonitoringFilters>(initialFilters)
  const [columns, setColumns] = useState<(typeof gridOptions)[number]>(2)
  const selectedConnection = connectionId
    ? webrtcConnections.find((connection) => String(connection.id) === connectionId)
    : undefined

  const updateRegion = (region: string) => {
    setSearchParams((current) => {
      const next = new URLSearchParams(current)
      const keyword = region.trim()
      if (keyword) next.set('keyword', keyword)
      else next.delete('keyword')
      return next
    }, { replace: true })
  }

  const resetFilters = () => {
    setFilters(initialFilters)
    setSearchParams((current) => {
      const next = new URLSearchParams(current)
      next.delete('keyword')
      return next
    }, { replace: true })
  }

  const filteredConnections = useMemo(
    () =>
      webrtcConnections.filter((connection) => {
        const matchesRegion =
          keywordFromUrl.length === 0 ||
          connection.location.toLowerCase().includes(keywordFromUrl.toLowerCase())
        const matchesRisk =
          filters.riskLevel === allValue || matchesRiskLevel(connection.riskLevel, filters.riskLevel)
        const matchesStatus =
          filters.connectionStatus === allValue ||
          connection.connectionStatus === filters.connectionStatus

        return matchesRegion && matchesRisk && matchesStatus
      }),
    [filters, keywordFromUrl],
  )

  return (
    <section className="page-stack">
      {connectionId && selectedConnection ? (
        <MonitoringDetailView
          connection={selectedConnection}
          onBack={() => navigate(ROUTE_PATHS.MONITORING)}
        />
      ) : connectionId ? (
        <section className="admin-card empty-state">
          <strong>해당 실시간 영상을 찾을 수 없습니다.</strong>
          <button type="button" className="primary-button" onClick={() => navigate(ROUTE_PATHS.MONITORING)}>전체 영상 보기</button>
        </section>
      ) : (
        <>
          <AdminSection title="실시간 모니터링">
            <div className="filter-grid">
              <label>
                지역
                <input
                  type="search"
                  value={keywordFromUrl}
                  onChange={(event) => updateRegion(event.target.value)}
                  placeholder="지역 검색"
                />
              </label>
              <label>
                위험도
                <select
                  value={filters.riskLevel}
                  onChange={(event) =>
                    setFilters((current) => ({
                      ...current,
                      riskLevel: event.target.value as MonitoringFilters['riskLevel'],
                    }))
                  }
                >
                  <option value={allValue}>전체</option>
                  {RISK_LEVEL_OPTIONS.map(({ value, label }) => (
                    <option key={value} value={value}>
                      {label}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                연결 상태
                <select
                  value={filters.connectionStatus}
                  onChange={(event) =>
                    setFilters((current) => ({
                      ...current,
                      connectionStatus: event.target.value as MonitoringFilters['connectionStatus'],
                    }))
                  }
                >
                  <option value={allValue}>전체</option>
                  {Object.entries(connectionStatusLabels).map(([value, label]) => (
                    <option key={value} value={value}>
                      {label}
                    </option>
                  ))}
                </select>
              </label>
              <button
                type="button"
                className="secondary-button"
                onClick={resetFilters}
              >
                초기화
              </button>
            </div>
            <div className="segmented-control" aria-label="화면 배치 선택">
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
          </AdminSection>

          <MonitoringGrid
            columns={columns}
            connections={filteredConnections}
            onSelect={(connection) => navigate(createMonitoringDetailPath(connection.id))}
          />
        </>
      )}
    </section>
  )
}

interface MonitoringGridProps {
  columns: (typeof gridOptions)[number]
  connections: WebRTCConnection[]
  onSelect: (connection: WebRTCConnection) => void
}

function MonitoringGrid({ columns, connections, onSelect }: MonitoringGridProps) {
  return (
    <article className="admin-card monitoring-grid-card">
      {connections.length > 0 ? (
        <div className={`monitoring-grid columns-${columns}`}>
          {connections.map((connection) => (
            <MonitoringVideoCard
              key={connection.id}
              connection={connection}
              onSelect={() => onSelect(connection)}
            />
          ))}
        </div>
      ) : (
        <p className="empty-state">조건에 맞는 영상이 없습니다.</p>
      )}
    </article>
  )
}

interface MonitoringVideoCardProps {
  connection: WebRTCConnection
  onSelect: () => void
}

function MonitoringVideoCard({ connection, onSelect }: MonitoringVideoCardProps) {
  return (
    <button type="button" className="video-card" onClick={onSelect}>
      {connection.connectionStatus === 'live' && <span className="live-badge">LIVE</span>}
      <strong>{connection.location || '정보 없음'}</strong>
      <RiskBadge value={connection.riskLevel} />
      <span>연결 시간 {connection.connectedAt || '정보 없음'}</span>
      <span>AI 감지 {connection.aiDetected ? '감지됨' : '없음'}</span>
      <span>품질 {connectionQualityLabels[connection.quality] ?? '정보 없음'}</span>
    </button>
  )
}

interface MonitoringDetailViewProps {
  connection: WebRTCConnection
  onBack: () => void
}

function MonitoringDetailView({ connection, onBack }: MonitoringDetailViewProps) {
  const { adminEmail } = useAuth()
  const [message, setMessage] = useState('')
  const [messageError, setMessageError] = useState('')
  const [isSending, setIsSending] = useState(false)

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
    if (!adminEmail) {
      setMessageError('관리자 정보를 확인할 수 없습니다.')
      return
    }

    setIsSending(true)
    setMessageError('')
    try {
      await sendAdminLiveMessage({
        connectionId: connection.id,
        reportId: connection.reportId,
        adminEmail,
        message: trimmedMessage,
      })
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

  return (
    <>
      <button type="button" className="secondary-button" onClick={onBack}>
        ← 전체 영상으로 돌아가기
      </button>

      <div className="monitoring-detail-layout">
        <article className="admin-card monitoring-featured-video">
          <div className="featured-video-frame">
            {connection.connectionStatus === 'live' && <span className="live-badge">LIVE</span>}
            <strong>Mock WebRTC 영상</strong>
            <span>{connection.location || '정보 없음'}</span>
          </div>
          <form className="live-message-form" onSubmit={sendMessage}>
            <label className="sr-only" htmlFor={`live-message-${connection.id}`}>현장 사용자에게 보낼 메시지</label>
            <input
              id={`live-message-${connection.id}`}
              type="text"
              maxLength={200}
              value={message}
              onChange={(event) => {
                setMessage(event.target.value)
                if (messageError) setMessageError('')
              }}
              onKeyDown={preventImeSubmit}
              placeholder="현장 사용자에게 전달할 메시지를 입력하세요."
              aria-describedby={messageError ? 'live-message-error' : undefined}
            />
            <button type="submit" className="primary-button" disabled={isSending || connection.connectionStatus !== 'live'}>
              {isSending ? '전송 중...' : '전송'}
            </button>
          </form>
          {messageError && <p id="live-message-error" className="live-message-error" role="alert">{messageError}</p>}
          <div className="detail-grid detail-grid--two-columns">
            <div>
              <span>위치</span>
              <strong>{connection.location || '정보 없음'}</strong>
            </div>
            <div>
              <span>연결 시간</span>
              <strong>{connection.connectedAt || '정보 없음'}</strong>
            </div>
          </div>
        </article>

        <aside className="admin-card monitor-detail">
          <h2>선택 영상 상세 정보</h2>
          <dl>
            <div>
              <dt>제보 ID</dt>
              <dd>{connection.reportId ? `#${connection.reportId}` : '정보 없음'}</dd>
            </div>
            <div>
              <dt>위치</dt>
              <dd>{connection.location || '정보 없음'}</dd>
            </div>
            <div>
              <dt>위험도</dt>
              <dd>{getRiskLabel(connection.riskLevel)}</dd>
            </div>
            <div>
              <dt>위험 점수</dt>
              <dd>{Number.isFinite(connection.riskScore) ? connection.riskScore : '정보 없음'}</dd>
            </div>
            <div>
              <dt>연결 상태</dt>
              <dd>{connectionStatusLabels[connection.connectionStatus] ?? '정보 없음'}</dd>
            </div>
            <div>
              <dt>연결 품질</dt>
              <dd>{connectionQualityLabels[connection.quality] ?? '정보 없음'}</dd>
            </div>
            <div>
              <dt>연결 시간</dt>
              <dd>{connection.connectedAt || '정보 없음'}</dd>
            </div>
            <div>
              <dt>AI 위험 감지 여부</dt>
              <dd>{connection.aiDetected ? '감지됨' : '없음'}</dd>
            </div>
            <div>
              <dt>AI 분석 결과</dt>
              <dd>{connection.aiAnalysisResult || '정보 없음'}</dd>
            </div>
            <div>
              <dt>AI 신뢰도</dt>
              <dd>
                {Number.isFinite(connection.confidence) ? `${connection.confidence}%` : '정보 없음'}
              </dd>
            </div>
          </dl>
        </aside>
      </div>
    </>
  )
}
