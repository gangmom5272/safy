import { useMemo, useState } from 'react'
import {
  connectionQualityLabels,
  connectionStatusLabels,
  riskLevelLabels,
  webrtcConnections,
} from '../../mocks/adminMockData'
import type { RiskLevel, WebRTCConnection, WebRTCConnectionStatus } from '../../types/admin'

const allValue = 'all'
const gridOptions = [2, 3, 5] as const

interface MonitoringFilters {
  region: string
  riskLevel: RiskLevel | typeof allValue
  connectionStatus: WebRTCConnectionStatus | typeof allValue
}

const initialFilters: MonitoringFilters = {
  region: '',
  riskLevel: allValue,
  connectionStatus: allValue,
}

export function MonitoringPage() {
  const [filters, setFilters] = useState<MonitoringFilters>(initialFilters)
  const [columns, setColumns] = useState<(typeof gridOptions)[number]>(2)
  const [selectedConnection, setSelectedConnection] = useState<WebRTCConnection | null>(null)

  const filteredConnections = useMemo(
    () =>
      webrtcConnections.filter((connection) => {
        const matchesRegion =
          filters.region.trim().length === 0 ||
          connection.location.toLowerCase().includes(filters.region.trim().toLowerCase())
        const matchesRisk =
          filters.riskLevel === allValue || connection.riskLevel === filters.riskLevel
        const matchesStatus =
          filters.connectionStatus === allValue ||
          connection.connectionStatus === filters.connectionStatus

        return matchesRegion && matchesRisk && matchesStatus
      }),
    [filters],
  )

  return (
    <section className="page-stack">
      {selectedConnection ? (
        <MonitoringDetailView
          connection={selectedConnection}
          onBack={() => setSelectedConnection(null)}
        />
      ) : (
        <>
          <article className="admin-card">
            <div className="card-heading">
              <div>
                <h2>실시간 모니터링</h2>
                <p>Mock WebRTC 연결 화면을 확인합니다.</p>
              </div>
            </div>
            <div className="filter-grid">
              <label>
                지역
                <input
                  type="search"
                  value={filters.region}
                  onChange={(event) =>
                    setFilters((current) => ({ ...current, region: event.target.value }))
                  }
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
                  {Object.entries(riskLevelLabels).map(([value, label]) => (
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
                onClick={() => setFilters(initialFilters)}
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
          </article>

          <MonitoringGrid
            columns={columns}
            connections={filteredConnections}
            onSelect={setSelectedConnection}
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
      <span className={`risk-badge risk-${connection.riskLevel}`}>
        {riskLevelLabels[connection.riskLevel] ?? '정보 없음'}
      </span>
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
          <div className="detail-grid">
            <div>
              <span>LIVE 여부</span>
              <strong>{connection.connectionStatus === 'live' ? 'LIVE' : 'LIVE 아님'}</strong>
            </div>
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
              <dd>{riskLevelLabels[connection.riskLevel] ?? '정보 없음'}</dd>
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
