import { NavLink } from 'react-router'
import { useAuth } from '../../auth/useAuth'
import { ROUTE_PATHS } from '../../router/paths'
import { SidebarIcon, type SidebarIconName } from './SidebarIcon'
import { SidebarProfileMenu } from './SidebarProfileMenu'

interface SidebarItem {
  label: string
  path: string
  end: boolean
  icon: SidebarIconName
}

/** 좌측 탭에 신규/미확인 건수를 붙일 경로. 값은 AdminLayout 이 실제 제보·라이브 목록에서 계산한다. */
export type SidebarBadgeCounts = Partial<Record<string, number>>

// OPERATOR 는 관제 화면, SUPER_ADMIN 은 인사 관리만 쓴다. 좌측 탭도 역할별로 분리한다.
// 계정 설정은 탭이 아니라 하단 프로필 메뉴에서 연다.
const operatorItems: SidebarItem[] = [
  { label: '홈', path: ROUTE_PATHS.DASHBOARD, end: true, icon: 'home' },
  { label: '실시간 모니터링', path: ROUTE_PATHS.MONITORING, end: true, icon: 'monitoring' },
  { label: '위험도 지도', path: ROUTE_PATHS.RISK_MAP, end: true, icon: 'map' },
  { label: '제보 목록', path: ROUTE_PATHS.REPORTS, end: false, icon: 'reports' },
  { label: '미배정', path: ROUTE_PATHS.QUEUE, end: true, icon: 'queue' },
  { label: '현장 제보 요청', path: ROUTE_PATHS.AREA_REPORT_REQUESTS, end: true, icon: 'request' },
]

const superAdminItems: SidebarItem[] = [
  { label: '담당자 관리', path: ROUTE_PATHS.OPERATORS, end: true, icon: 'user' },
]

/** 좁은 배지에 세 자리가 넘어가면 줄바꿈이 생기므로 99 초과는 99+ 로 줄인다. */
function formatBadgeCount(count: number): string {
  return count > 99 ? '99+' : String(count)
}

interface AdminSidebarProps {
  collapsed: boolean
  onToggleCollapsed: () => void
  badgeCounts?: SidebarBadgeCounts
}

export function AdminSidebar({ collapsed, onToggleCollapsed, badgeCounts }: AdminSidebarProps) {
  const { user } = useAuth()
  const isSuperAdmin = user?.role === 'SUPER_ADMIN'
  const sidebarItems = isSuperAdmin ? superAdminItems : operatorItems

  return (
    <aside
      className={collapsed ? 'admin-sidebar admin-sidebar--collapsed' : 'admin-sidebar'}
      aria-label="관리자 메뉴"
    >
      <div className="admin-sidebar__brand">
        <span className="admin-sidebar__brand-icon">
          <SidebarIcon name="shield" />
        </span>
        <span className="admin-sidebar__brand-copy">
          <strong>SAFY</strong>
        </span>
      </div>

      <nav className="admin-sidebar__nav">
        {sidebarItems.map((item) => {
          const badgeCount = badgeCounts?.[item.path] ?? 0

          return (
            <NavLink
              key={item.path}
              to={item.path}
              end={item.end}
              className={({ isActive }) => (isActive ? 'active' : undefined)}
              // 접힌 상태에서는 메뉴명이 보이지 않고, 배지도 점으로만 표시되므로 둘 다 이름에 담는다.
              aria-label={badgeCount > 0 ? `${item.label} 알림 ${badgeCount}건` : item.label}
            >
              <span className="admin-sidebar__nav-icon-slot">
                <SidebarIcon name={item.icon} className="admin-sidebar__nav-icon" />
                {badgeCount > 0 && <span className="admin-sidebar__nav-dot" aria-hidden="true" />}
              </span>
              <span className="admin-sidebar__label">{item.label}</span>
              {badgeCount > 0 && (
                <span className="admin-sidebar__nav-badge" aria-hidden="true">
                  {formatBadgeCount(badgeCount)}
                </span>
              )}
              <span className="admin-sidebar__tooltip" aria-hidden="true">{item.label}</span>
            </NavLink>
          )
        })}
      </nav>

      <div className="admin-sidebar__footer">
        <SidebarProfileMenu collapsed={collapsed} />
        <button
          type="button"
          className="admin-sidebar__collapse"
          onClick={onToggleCollapsed}
          aria-label={collapsed ? '사이드바 펼치기' : '사이드바 접기'}
          aria-expanded={!collapsed}
        >
          <SidebarIcon name={collapsed ? 'chevron-right' : 'chevron-left'} />
          <span className="admin-sidebar__label">접기</span>
        </button>
      </div>
    </aside>
  )
}
