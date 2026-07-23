import { useMemo, useState } from 'react'
import {
  connectionQualityLabels,
  connectionStatusLabels,
  riskLevelLabels,
  webrtcConnections,
} from '../../mocks/adminMockData'
import type { RiskLevel, WebRTCConnectionStatus } from '../../types/admin'

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
  const [selectedId, setSelectedId] = useState(webrtcConnections[0]?.id)

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

  const selectedConnection =
    filteredConnections.find((connection) => connection.id === selectedId) ??
    filteredConnections[0]

  return (
    <section className="page-stack">
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
          <button type="button" className="secondary-button" onClick={() => setFilters(initialFilters)}>
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

      <div className="monitoring-layout">
        <article className="admin-card">
          <div className={`monitoring-grid columns-${columns}`}>
            {filteredConnections.map((connection) => (
              <button
                key={connection.id}
                type="button"
                className={`video-card ${selectedConnection?.id === connection.id ? 'selected' : ''}`}
                onClick={() => setSelectedId(connection.id)}
              >
                {connection.connectionStatus === 'live' && <span className="live-badge">LIVE</span>}
                <strong>{connection.location}</strong>
                <span className={`risk-badge risk-${connection.riskLevel}`}>
                  {riskLevelLabels[connection.riskLevel]}
                </span>
                <span>연결 시간 {connection.connectedAt}</span>
                <span>AI 감지 {connection.aiDetected ? '감지됨' : '없음'}</span>
                <span>품질 {connectionQualityLabels[connection.quality]}</span>
              </button>
            ))}
          </div>
        </article>

        <aside className="admin-card monitor-detail">
          <h2>선택 영상 상세</h2>
          {selectedConnection ? (
            <dl>
              <div>
                <dt>제보 ID</dt>
                <dd>#{selectedConnection.reportId}</dd>
              </div>
              <div>
                <dt>위치</dt>
                <dd>{selectedConnection.location}</dd>
              </div>
              <div>
                <dt>위험도</dt>
                <dd>{riskLevelLabels[selectedConnection.riskLevel]}</dd>
              </div>
              <div>
                <dt>AI 분석 결과</dt>
                <dd>{selectedConnection.aiAnalysisResult}</dd>
              </div>
              <div>
                <dt>신뢰도</dt>
                <dd>{selectedConnection.confidence}%</dd>
              </div>
              <div>
                <dt>연결 시간</dt>
                <dd>{selectedConnection.connectedAt}</dd>
              </div>
              <div>
                <dt>연결 상태</dt>
                <dd>{connectionStatusLabels[selectedConnection.connectionStatus]}</dd>
              </div>
            </dl>
          ) : (
            <p>선택할 영상이 없습니다.</p>
          )}
        </aside>
      </div>
    </section>
  )
}
