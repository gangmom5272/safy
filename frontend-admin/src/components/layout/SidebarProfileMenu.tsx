import { useEffect, useId, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { useAuth } from '../../auth/useAuth'
import { ROUTE_PATHS } from '../../router/paths'
import { SidebarIcon } from './SidebarIcon'

interface SidebarProfileMenuProps {
  collapsed: boolean
}

/**
 * 사이드바 하단 프로필. 좌측 탭에서 '계정 설정'을 뺀 대신, 프로필을 누르면
 * 계정 설정과 로그아웃만 담은 작은 메뉴를 띄운다. 로그아웃은 기존 useAuth().logout 을 그대로 쓴다.
 */
export function SidebarProfileMenu({ collapsed }: SidebarProfileMenuProps) {
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const { user, adminName, logout } = useAuth()
  const [isOpen, setIsOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)
  const menuId = useId()

  const isSuperAdmin = user?.role === 'SUPER_ADMIN'
  const displayName = user?.displayName || adminName || 'SAFY Super Admin'
  const roleLabel = isSuperAdmin ? '최고 관리자' : '관리자'
  const isSettingsActive = pathname === ROUTE_PATHS.SETTINGS

  // 팝오버는 바깥 클릭과 ESC 로 닫는다. 열려 있을 때만 리스너를 붙인다.
  useEffect(() => {
    if (!isOpen) return

    const handlePointerDown = (event: MouseEvent) => {
      if (!containerRef.current?.contains(event.target as Node)) setIsOpen(false)
    }
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setIsOpen(false)
    }

    document.addEventListener('mousedown', handlePointerDown)
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('mousedown', handlePointerDown)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [isOpen])

  const handleLogout = async () => {
    setIsOpen(false)
    await logout().catch(() => undefined)
    navigate(ROUTE_PATHS.LOGIN, { replace: true })
  }

  return (
    <div className="admin-sidebar__profile" ref={containerRef}>
      <button
        type="button"
        className="admin-sidebar__account"
        aria-haspopup="menu"
        aria-expanded={isOpen}
        aria-controls={isOpen ? menuId : undefined}
        aria-label={`${displayName} 계정 메뉴`}
        title={collapsed ? `${displayName} 계정 메뉴` : undefined}
        onClick={() => setIsOpen((open) => !open)}
      >
        <span className="admin-sidebar__avatar">
          <SidebarIcon name="user" />
        </span>
        <span className="admin-sidebar__account-copy">
          <strong title={displayName}>{displayName}</strong>
          <span>{roleLabel}</span>
        </span>
      </button>

      {isOpen && (
        <div className="admin-sidebar__profile-menu" id={menuId} role="menu">
          <button
            type="button"
            role="menuitem"
            className={isSettingsActive ? 'active' : undefined}
            aria-current={isSettingsActive ? 'page' : undefined}
            onClick={() => {
              setIsOpen(false)
              navigate(ROUTE_PATHS.SETTINGS)
            }}
          >
            <SidebarIcon name="settings" />
            <span>계정 설정</span>
          </button>
          <button type="button" role="menuitem" onClick={() => void handleLogout()}>
            <SidebarIcon name="logout" />
            <span>로그아웃</span>
          </button>
        </div>
      )}
    </div>
  )
}
