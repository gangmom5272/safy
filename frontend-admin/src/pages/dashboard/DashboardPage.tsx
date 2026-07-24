import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router'
import {
  adminReports,
  connectionStatusLabels,
  emergencyBanner,
  reportStatusLabels,
  riskLevelLabels,
  riskMarkers,
  webrtcConnections,
} from '../../mocks/adminMockData'
import { ROUTE_PATHS, createReportDetailPath } from '../../router/paths'

export function DashboardPage() {
  const navigate = useNavigate()
  const liveConnections = webrtcConnections.filter(
    (connection) => connection.connectionStatus === 'live',
  )
  const waitingConnections = webrtcConnections.filter(
    (connection) => connection.connectionStatus === 'waiting',
  )
  const recentDangerReports = adminReports.filter(
    (report) => report.riskLevel === 'danger' || report.riskLevel === 'warning',
  )
  const [requestedConnectionIds, setRequestedConnectionIds] = useState<number[]>([])

  const queueRows = useMemo(
    () =>
      waitingConnections.map((connection) => ({
        ...connection,
        isRequested: requestedConnectionIds.includes(connection.id),
      })),
    [requestedConnectionIds, waitingConnections],
  )

  const requestConnection = (connectionId: number) => {
    setRequestedConnectionIds((currentIds) =>
      currentIds.includes(connectionId) ? currentIds : [...currentIds, connectionId],
    )
  }

  return (
    <section className="page-stack">
      <div className="emergency-banner">{emergencyBanner}</div>

      <div className="dashboard-grid">
        <article className="admin-card dashboard-map-card">
          <div className="card-heading">
            <div>
              <h2>실시간 위험도 지도</h2>
              <p>주요 지역 위험도 마커</p>
            </div>
            <button type="button" className="text-button" onClick={() => navigate(ROUTE_PATHS.RISK_MAP)}>
              전체 보기
            </button>
          </div>
          <button
            type="button"
            className="map-preview"
            onClick={() => navigate(ROUTE_PATHS.RISK_MAP)}
            aria-label="위험도 지도 전체 보기"
          >
            {riskMarkers.map((marker) => (
              <span
                key={marker.id}
                className={`map-marker risk-${marker.riskLevel}`}
                style={{ top: `${marker.top}%`, left: `${marker.left}%` }}
              >
                {marker.location}
              </span>
            ))}
          </button>
        </article>

        <article className="admin-card">
          <div className="card-heading">
            <div>
              <h2>실시간 연결 화면</h2>
              <p>현재 연결된 현장 영상</p>
            </div>
          </div>
          <div className="live-preview-grid">
            {liveConnections.map((connection) => (
              <button
                key={connection.id}
                type="button"
                className="video-card"
                onClick={() => navigate(ROUTE_PATHS.MONITORING)}
              >
                <span className="live-badge">LIVE</span>
                <strong>{connection.location}</strong>
                <span className={`risk-badge risk-${connection.riskLevel}`}>
                  {riskLevelLabels[connection.riskLevel]}
                </span>
                <span>연결 시간 {connection.connectedAt}</span>
                <span>AI 위험 감지 {connection.aiDetected ? '감지됨' : '없음'}</span>
              </button>
            ))}
          </div>
        </article>
      </div>

      <div className="dashboard-grid">
        <article className="admin-card">
          <div className="card-heading">
            <div>
              <h2>WebRTC 연결 대기열</h2>
              <p>관리자가 연결 요청을 보낼 수 있습니다.</p>
            </div>
          </div>
          <div className="queue-list">
            {queueRows.map((connection, index) => (
              <div key={connection.id} className="queue-row">
                <span>{index + 1}</span>
                <strong>{connection.location}</strong>
                <span className={`risk-badge risk-${connection.riskLevel}`}>
                  {riskLevelLabels[connection.riskLevel]}
                </span>
                <span>{connection.connectedAt}</span>
                <button
                  type="button"
                  className="primary-button"
                  onClick={() => requestConnection(connection.id)}
                  disabled={connection.isRequested}
                >
                  {connection.isRequested
                    ? connectionStatusLabels.requested
                    : '연결'}
                </button>
              </div>
            ))}
          </div>
        </article>

        <article className="admin-card">
          <div className="card-heading">
            <div>
              <h2>최근 위험 제보</h2>
              <p>위험도가 높은 제보 목록</p>
            </div>
          </div>
          <div className="table-wrap">
            <table className="admin-table">
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
                      <span className={`risk-badge risk-${report.riskLevel}`}>
                        {riskLevelLabels[report.riskLevel]}
                      </span>
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
        </article>
      </div>
    </section>
  )
}
