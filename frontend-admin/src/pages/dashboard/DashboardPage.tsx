import { useCallback, type KeyboardEvent } from 'react'
import { useNavigate, useOutletContext } from 'react-router'
import { reportStatusLabels } from '../../mocks/adminMockData'
import type { WebRTCConnection } from '../../types/admin'
import { ROUTE_PATHS, createMonitoringDetailPath, createReportDetailPath } from '../../router/paths'
import { AdminSection } from '../../components/admin/AdminSection'
import { RiskBadge } from '../../components/admin/RiskBadge'
import { ReportMap } from '../../components/admin/ReportMap'
import { formatAdminTimestamp } from '../../utils/adminDate'
import { isNewReport } from '../../utils/adminVerification'
import { LiveViewer } from '../monitoring/LiveViewer'
import { useDashboardReports } from './useDashboardReports'
import type { AdminLayoutOutletContext } from '../../layouts/AdminLayout'

export function DashboardPage() {
  const navigate = useNavigate()
  const { markDashboardUpdated } = useOutletContext<AdminLayoutOutletContext>()
  const { reports, connections, isLoading, loadError, markChecked } = useDashboardReports(markDashboardUpdated)

  const activeConnections = connections.filter(
    (connection) => connection.connectionStatus !== 'ended',
  )

  const openMonitoring = (connection: WebRTCConnection) => {
    navigate(createMonitoringDetailPath(connection.id))
  }

  // 상세로 이동하는 순간 확인 처리도 함께 반영해 지도와 테이블 표시가 동시에 바뀐다.
  const openReportDetail = useCallback((reportId: number) => {
    markChecked(reportId)
    navigate(createReportDetailPath(reportId))
  }, [markChecked, navigate])

  return (
    <section className="page-stack">
      {loadError && <p className="admin-card empty-state" role="alert">{loadError}</p>}
      <div className="home-content-grid">
        <AdminSection
          title="실시간 위험도 지도"
          className="home-map-section"
          cardClassName="dashboard-map-card"
          action={<button type="button" className="text-button" onClick={() => navigate(ROUTE_PATHS.RISK_MAP)}>전체 보기 &gt;</button>}
        >
          <ReportMap
            className="map-preview"
            reports={reports}
            showLegend
            onSelect={(report) => openReportDetail(report.id)}
          />
        </AdminSection>

        <AdminSection title="최근 배정 제보" className="home-recent-section">
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
                {reports.map((report) => (
                  <tr
                    key={report.id}
                    className={`clickable-row${isNewReport(report) ? ' recent-reports-table__row--new' : ''}`}
                    onClick={() => openReportDetail(report.id)}
                  >
                    <td>
                      {report.location}
                      {isNewReport(report) && <span className="new-badge">NEW</span>}
                    </td>
                    <td>
                      <RiskBadge value={report.riskLevel} score={report.riskScore} />
                    </td>
                    <td>{formatAdminTimestamp(report.reportedAt) || '정보 없음'}</td>
                    <td>{reportStatusLabels[report.status]}</td>
                    <td>
                      <button
                        type="button"
                        className="text-button"
                        onClick={(event) => {
                          event.stopPropagation()
                          openReportDetail(report.id)
                        }}
                      >
                        상세
                      </button>
                    </td>
                  </tr>
                ))}
                {!isLoading && reports.length === 0 && (
                  <tr>
                    <td colSpan={5} className="empty-state">현재 배정된 제보가 없습니다.</td>
                  </tr>
                )}
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
                  uploadedAt={reports.find((report) => report.id === connection.reportId)?.reportedAt}
                  onOpenMonitoring={() => openMonitoring(connection)}
                />
              ))}
            </div>
          ) : isLoading ? (
            <div className="empty-state" role="status">실시간 연결을 불러오는 중입니다...</div>
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
        <RiskBadge value={connection.riskLevel} score={connection.riskScore} />
      </header>
      <div className="live-connection-item__media">
        {isConnected && <span className="live-badge">LIVE</span>}
        <LiveViewer connection={connection} compact />
      </div>
      <footer className="live-connection-item__footer">
        <time dateTime={uploadedAt}>{uploadTimestamp ? `업로드 ${uploadTimestamp}` : '업로드 시간 정보 없음'}</time>
      </footer>
    </article>
  )
}
