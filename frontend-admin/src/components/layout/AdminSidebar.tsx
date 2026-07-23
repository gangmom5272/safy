import { NavLink } from 'react-router'
import { systemStatus } from '../../mocks/adminMockData'
import { ROUTE_PATHS } from '../../router/paths'

const sidebarItems = [
  { label: '대시보드', path: ROUTE_PATHS.DASHBOARD, end: true },
  { label: '제보 관리', path: ROUTE_PATHS.REPORTS, end: false },
  { label: '실시간 모니터링', path: ROUTE_PATHS.MONITORING, end: true },
  { label: '위험도 지도', path: ROUTE_PATHS.RISK_MAP, end: true },
]

export function AdminSidebar() {
  return (
    <aside className="admin-sidebar" aria-label="관리자 메뉴">
      <div className="admin-sidebar__brand">
        <strong>SAFY</strong>
        <span>관리자</span>
      </div>

      <nav className="admin-sidebar__nav">
        {sidebarItems.map((item) => (
          <NavLink
            key={item.path}
            to={item.path}
            end={item.end}
            className={({ isActive }) => (isActive ? 'active' : undefined)}
          >
            {item.label}
          </NavLink>
        ))}
      </nav>

      <section className="system-status" aria-labelledby="system-status-title">
        <h2 id="system-status-title">시스템 상태</h2>
        <dl>
          <div>
            <dt>서버 상태</dt>
            <dd>{systemStatus.serverStatus}</dd>
          </div>
          <div>
            <dt>WebRTC 연결 수</dt>
            <dd>{systemStatus.webrtcConnections}</dd>
          </div>
          <div>
            <dt>활성 제보 수</dt>
            <dd>{systemStatus.activeReports}</dd>
          </div>
          <div>
            <dt>AI 분석 상태</dt>
            <dd>{systemStatus.aiAnalysisStatus}</dd>
          </div>
        </dl>
      </section>
    </aside>
  )
}
