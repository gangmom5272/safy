import type { ReactNode } from 'react'
import { NavLink, useNavigate } from 'react-router'
import { useAuth } from '../../auth/useAuth'
import { ROUTE_PATHS } from '../../router/paths'

type SidebarIconName =
  | 'shield'
  | 'home'
  | 'reports'
  | 'queue'
  | 'monitoring'
  | 'map'
  | 'settings'
  | 'user'
  | 'logout'

interface SidebarIconProps {
  name: SidebarIconName
  className?: string
}

function SidebarIcon({ name, className }: SidebarIconProps) {
  const paths: Record<SidebarIconName, ReactNode> = {
    shield: <path d="M12 3 5.5 5.7v5.6c0 4.2 2.7 7.9 6.5 9.2 3.8-1.3 6.5-5 6.5-9.2V5.7L12 3Z" />,
    home: <path d="m3 11.5 9-8 9 8V20a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1v-8.5Z" />,
    reports: (
      <>
        <path d="M9 6h11M9 12h11M9 18h11" />
        <path d="m3.5 6 1 1 2-2M3.5 12l1 1 2-2M3.5 18l1 1 2-2" />
      </>
    ),
    queue: (
      <>
        <path d="M5 4h14l2 8v6a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-6l2-8Z" />
        <path d="M3 12h4.5l1.5 3h6l1.5-3H21" />
      </>
    ),
    monitoring: (
      <>
        <rect x="3" y="6" width="13" height="12" rx="2" />
        <path d="m16 10 5-3v10l-5-3" />
      </>
    ),
    map: (
      <>
        <path d="m3 6 6-3 6 3 6-3v15l-6 3-6-3-6 3V6Z" />
        <path d="M9 3v15M15 6v15" />
      </>
    ),
    settings: (
      <>
        <circle cx="12" cy="12" r="3" />
        <path d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1-2.8 2.8-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.6v.2h-4V21a1.7 1.7 0 0 0-1-1.6 1.7 1.7 0 0 0-1.9.3l-.1.1L4.2 17l.1-.1a1.7 1.7 0 0 0 .3-1.9A1.7 1.7 0 0 0 3 14H2.8v-4H3a1.7 1.7 0 0 0 1.6-1 1.7 1.7 0 0 0-.3-1.9L4.2 7 7 4.2l.1.1A1.7 1.7 0 0 0 9 4.6 1.7 1.7 0 0 0 10 3v-.2h4V3a1.7 1.7 0 0 0 1 1.6 1.7 1.7 0 0 0 1.9-.3l.1-.1L19.8 7l-.1.1a1.7 1.7 0 0 0-.3 1.9 1.7 1.7 0 0 0 1.6 1h.2v4H21a1.7 1.7 0 0 0-1.6 1Z" />
      </>
    ),
    user: (
      <>
        <circle cx="12" cy="8" r="4" />
        <path d="M4 21a8 8 0 0 1 16 0" />
      </>
    ),
    logout: (
      <>
        <path d="M10 17l5-5-5-5M15 12H3" />
        <path d="M14 3h5a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-5" />
      </>
    ),
  }

  return (
    <svg
      className={className}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      {paths[name]}
    </svg>
  )
}

const sidebarItems = [
  { label: '홈', path: ROUTE_PATHS.DASHBOARD, end: true, icon: 'home' },
  { label: '제보 목록', path: ROUTE_PATHS.REPORTS, end: false, icon: 'reports' },
  { label: '공용 큐', path: ROUTE_PATHS.QUEUE, end: true, icon: 'queue' },
  { label: '실시간 모니터링', path: ROUTE_PATHS.MONITORING, end: true, icon: 'monitoring' },
  { label: '위험도 지도', path: ROUTE_PATHS.RISK_MAP, end: true, icon: 'map' },
  { label: '시스템 설정', path: ROUTE_PATHS.SETTINGS, end: true, icon: 'settings' },
] satisfies Array<{
  label: string
  path: string
  end: boolean
  icon: SidebarIconName
}>

export function AdminSidebar() {
  const navigate = useNavigate()
  const { user, adminName, logout } = useAuth()
  const displayName = user?.displayName || adminName || 'SAFY Super Admin'
  const roleLabel = user?.role === 'SUPER_ADMIN' ? '최고 관리자' : '관리자'

  const handleLogout = async () => {
    await logout().catch(() => undefined)
    navigate(ROUTE_PATHS.LOGIN, { replace: true })
  }

  return (
    <aside className="admin-sidebar" aria-label="관리자 메뉴">
      <div className="admin-sidebar__brand">
        <span className="admin-sidebar__brand-icon">
          <SidebarIcon name="shield" />
        </span>
        <span className="admin-sidebar__brand-copy">
          <strong>SAFY</strong>
          <span>관제 콘솔 · 성동구</span>
        </span>
      </div>

      <nav className="admin-sidebar__nav">
        {sidebarItems.map((item) => (
          <NavLink
            key={item.path}
            to={item.path}
            end={item.end}
            className={({ isActive }) => (isActive ? 'active' : undefined)}
          >
            <SidebarIcon name={item.icon} className="admin-sidebar__nav-icon" />
            <span>{item.label}</span>
          </NavLink>
        ))}
      </nav>

      <div className="admin-sidebar__account">
        <span className="admin-sidebar__avatar">
          <SidebarIcon name="user" />
        </span>
        <span className="admin-sidebar__account-copy">
          <strong title={displayName}>{displayName}</strong>
          <span>{roleLabel}</span>
        </span>
        <button
          type="button"
          className="admin-sidebar__logout"
          onClick={() => void handleLogout()}
          aria-label={`${displayName} 계정 로그아웃`}
          title="로그아웃"
        >
          <SidebarIcon name="logout" />
        </button>
      </div>
    </aside>
  )
}
