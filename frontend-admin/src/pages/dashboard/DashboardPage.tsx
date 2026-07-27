import { type KeyboardEvent } from 'react'
import { useNavigate } from 'react-router'
import {
  adminReports,
  reportStatusLabels,
  riskMarkers,
  webrtcConnections,
} from '../../mocks/adminMockData'
import type { WebRTCConnection } from '../../types/admin'
import { ROUTE_PATHS, createMonitoringDetailPath, createReportDetailPath } from '../../router/paths'
import { AdminSection } from '../../components/admin/AdminSection'
import { RiskBadge } from '../../components/admin/RiskBadge'
import { getRiskConfig, isHighRisk } from '../../utils/adminRisk'
import { formatAdminTimestamp } from '../../utils/adminDate'

export function DashboardPage() {
  const navigate = useNavigate()
  const activeConnections = webrtcConnections.filter(
    (connection) => connection.connectionStatus !== 'ended',
  )
  const recentDangerReports = adminReports.filter((report) => isHighRisk(report.riskLevel))

  const openMonitoring = (connection: WebRTCConnection) => {
    navigate(createMonitoringDetailPath(connection.id))
  }

  return (
    <section className="page-stack">
      <div className="home-content-grid">
        <AdminSection
          title="실시간 위험도 지도"
          className="home-map-section"
          cardClassName="dashboard-map-card"
          action={<button type="button" className="text-button" onClick={() => navigate(ROUTE_PATHS.RISK_MAP)}>전체 보기 &gt;</button>}
        >
          <button
            type="button"
            className="map-preview"
            onClick={() => navigate(ROUTE_PATHS.RISK_MAP)}
            aria-label="위험도 지도 전체 보기"
          >
            {riskMarkers.map((marker) => (
              <span
                key={marker.id}
                className="map-marker"
                style={{ top: `${marker.top}%`, left: `${marker.left}%`, backgroundColor: getRiskConfig(marker.riskLevel)?.markerColor ?? '#64748B', color: getRiskConfig(marker.riskLevel)?.markerTextColor ?? '#FFFFFF' }}
              >
                {marker.location}
              </span>
            ))}
          </button>
        </AdminSection>

        <AdminSection title="최근 위험 제보" className="home-recent-section">
          <div className="table-wrap">
            <table className="admin-table recent-reports-table">
              <thead>
                <tr>
                  <th>위치</th>
                  <th>위험도</th>
                  <th>제보 시간</th>
                  <th>처리 상태</th>
                  <th>상세 보기</th>
                </tr>
              </thead>
              <tbody>
                {recentDangerReports.map((report) => (
                  <tr
                    key={report.id}
                    className="clickable-row"
                    onClick={() => navigate(createReportDetailPath(report.id))}
                  >
                    <td>{report.location}</td>
                    <td>
                      <RiskBadge value={report.riskLevel} />
                    </td>
                    <td>{report.reportedAt}</td>
                    <td>{reportStatusLabels[report.status]}</td>
                    <td>
                      <button
                        type="button"
                        className="text-button"
                        onClick={(event) => {
                          event.stopPropagation()
                          navigate(createReportDetailPath(report.id))
                        }}
                      >
                        상세
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </AdminSection>

        <AdminSection
          title="실시간 연결"
          className="home-live-section"
          cardClassName="live-connection-panel"
          action={<button type="button" className="text-button" onClick={() => navigate(ROUTE_PATHS.MONITORING)}>전체 보기 &gt;</button>}
        >
          {activeConnections.length > 0 ? (
            <div className="live-connection-list">
              {activeConnections.map((connection) => (
                <LiveConnectionCard
                  key={connection.id}
                  connection={connection}
                  uploadedAt={adminReports.find((report) => report.id === connection.reportId)?.reportedAt}
                  onOpenMonitoring={() => openMonitoring(connection)}
                />
              ))}
            </div>
          ) : (
            <div className="empty-state">현재 실시간 연결이 없습니다.</div>
          )}
        </AdminSection>
      </div>

    </section>
  )
}

interface LiveConnectionCardProps {
  connection: WebRTCConnection
  uploadedAt?: string
  onOpenMonitoring: () => void
}

function LiveConnectionCard({ connection, uploadedAt, onOpenMonitoring }: LiveConnectionCardProps) {
  const isConnected = connection.connectionStatus === 'live'
  const uploadTimestamp = formatAdminTimestamp(uploadedAt)
  const handleKeyDown = (event: KeyboardEvent<HTMLElement>) => {
    if (event.target !== event.currentTarget) return
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      onOpenMonitoring()
    }
  }

  return (
    <article
      className="live-connection-item"
      role="link"
      data-report-id={connection.reportId}
      tabIndex={0}
      onClick={onOpenMonitoring}
      onKeyDown={handleKeyDown}
      aria-label={`${connection.location || `제보 #${connection.reportId}`} 실시간 모니터링 보기`}
    >
      <header className="live-connection-item__header">
        <strong className="live-connection-item__location">{connection.location || `제보 #${connection.reportId}`}</strong>
        <RiskBadge value={connection.riskLevel} />
      </header>
      <div className="live-connection-item__media" aria-hidden="true">
        {isConnected && <span className="live-badge">LIVE</span>}
        <span>WebRTC</span>
      </div>
      <footer className="live-connection-item__footer">
        <time dateTime={uploadedAt}>{uploadTimestamp ? `업로드 ${uploadTimestamp}` : '업로드 시간 정보 없음'}</time>
      </footer>
    </article>
  )
}
