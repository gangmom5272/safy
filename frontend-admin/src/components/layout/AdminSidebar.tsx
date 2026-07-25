import { Link, NavLink } from 'react-router'
import { dashboardStats } from '../../mocks/adminMockData'
import { ROUTE_PATHS } from '../../router/paths'

const sidebarItems = [
  { label: '대시보드', path: ROUTE_PATHS.DASHBOARD, end: true },
  { label: '제보 관리', path: ROUTE_PATHS.REPORTS, end: false },
  { label: '실시간 모니터링', path: ROUTE_PATHS.MONITORING, end: true },
  { label: '위험도 지도', path: ROUTE_PATHS.RISK_MAP, end: true },
  { label: '시스템 설정', path: ROUTE_PATHS.SETTINGS, end: true },
]

const summaryItems = [
  { label: '전체 제보', value: dashboardStats.totalReports, unit: '건', path: ROUTE_PATHS.REPORTS },
  { label: '위험 제보', value: dashboardStats.dangerReports, unit: '건', path: ROUTE_PATHS.REPORTS },
  { label: 'WebRTC', value: dashboardStats.webrtcConnections, unit: '건', path: ROUTE_PATHS.MONITORING },
  { label: '위험 지역', value: dashboardStats.highRiskAreas, unit: '곳', path: ROUTE_PATHS.RISK_MAP },
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

      <section className="sidebar-summary" aria-labelledby="sidebar-summary-title">
        <h2 id="sidebar-summary-title">주요 현황</h2>
        <div className="sidebar-summary__grid">
          {summaryItems.map((item) => (
            <Link key={item.label} to={item.path} className="sidebar-summary__item">
              <span>{item.label}</span>
              <strong>
                {item.value.toLocaleString()}
                <small>{item.unit}</small>
              </strong>
            </Link>
          ))}
        </div>
      </section>
    </aside>
  )
}
