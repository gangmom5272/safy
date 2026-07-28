import { useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { useAuth } from '../../auth/useAuth'
import { AdminModal } from '../admin/AdminModal'
import { adminReports, emergencyBanner } from '../../mocks/adminMockData'
import { ROUTE_PATHS } from '../../router/paths'
import { isEmergencyRisk } from '../../utils/adminRisk'

const pageTitleByPath: Record<string, string> = {
  [ROUTE_PATHS.DASHBOARD]: '홈',
  [ROUTE_PATHS.REPORTS]: '제보 목록',
  [ROUTE_PATHS.MONITORING]: '실시간 모니터링',
  [ROUTE_PATHS.RISK_MAP]: '위험도 지도',
  [ROUTE_PATHS.SETTINGS]: '시스템 설정',
}

function getPageTitle(pathname: string) {
  if (pathname.startsWith(ROUTE_PATHS.REPORTS)) {
    return pathname === ROUTE_PATHS.REPORTS ? '제보 목록' : '제보 상세'
  }

  if (pathname.startsWith(ROUTE_PATHS.MONITORING)) {
    return '실시간 모니터링'
  }

  return pageTitleByPath[pathname] ?? '관리자 페이지'
}

function SirenIcon() {
  return (
    <svg className="header-action-icon" viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false">
      <path d="M8 15V10a4 4 0 0 1 8 0v5" />
      <path d="M6 19h12M7 15h10l1 4H6l1-4ZM12 2v2M4.9 4.9l1.4 1.4M19.1 4.9l-1.4 1.4M3 11H1M23 11h-2" />
    </svg>
  )
}

function LogOutIcon() {
  return (
    <svg className="header-action-icon" viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false">
      <path d="M10 17l5-5-5-5M15 12H3" />
      <path d="M14 3h5a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-5" />
    </svg>
  )
}

export function AdminHeader() {
  const { pathname } = useLocation()
  const navigate = useNavigate()
  const { adminName, logout } = useAuth()
  const [isProfileOpen, setIsProfileOpen] = useState(false)
  const [isEmergencyOpen, setIsEmergencyOpen] = useState(false)
  const profileRef = useRef<HTMLDivElement>(null)
  const dangerReports = adminReports.filter((report) => isEmergencyRisk(report.riskLevel))
  const displayName = adminName || '관리자'

  useEffect(() => {
    if (!isProfileOpen) return

    const closeOnOutsideClick = (event: PointerEvent) => {
      if (!profileRef.current?.contains(event.target as Node)) setIsProfileOpen(false)
    }
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setIsProfileOpen(false)
    }

    document.addEventListener('pointerdown', closeOnOutsideClick)
    document.addEventListener('keydown', closeOnEscape)
    return () => {
      document.removeEventListener('pointerdown', closeOnOutsideClick)
      document.removeEventListener('keydown', closeOnEscape)
    }
  }, [isProfileOpen])

  const handleLogout = () => {
    logout()
    navigate(ROUTE_PATHS.LOGIN, { replace: true })
  }

  return (
    <header className="admin-header">
      <div>
        <p className="admin-header__eyebrow">SAFY Admin Console</p>
        <h1>{getPageTitle(pathname)}</h1>
      </div>
      <div className="admin-header__actions">
        <button
          type="button"
          className="emergency-alert-button"
          onClick={() => setIsEmergencyOpen(true)}
          aria-label={`긴급 위험 알림 ${dangerReports.length}건 확인`}
        >
          <SirenIcon />
          <span>긴급 위험 알림</span>
          <strong>{dangerReports.length}건</strong>
        </button>
        <div className="admin-profile-menu" ref={profileRef}>
          <button
            type="button"
            className="admin-profile-trigger"
            onClick={() => setIsProfileOpen((current) => !current)}
            aria-haspopup="menu"
            aria-expanded={isProfileOpen}
            aria-label="관리자 계정 메뉴 열기"
          >
            {displayName} 관리자님
          </button>
          {isProfileOpen && (
            <div className="admin-profile-dropdown" role="menu">
              <button type="button" role="menuitem" onClick={handleLogout}>
                <LogOutIcon />
                <span>로그아웃</span>
              </button>
            </div>
          )}
        </div>
      </div>
      <AdminModal
        open={isEmergencyOpen}
        title={`긴급 위험 알림 ${dangerReports.length}건`}
        onClose={() => setIsEmergencyOpen(false)}
        footer={
          <button type="button" className="primary-button" onClick={() => setIsEmergencyOpen(false)}>
            닫기
          </button>
        }
      >
        <p className="emergency-alert-message">{emergencyBanner}</p>
        {dangerReports.length > 0 ? (
          <ul className="emergency-report-list">
            {dangerReports.map((report) => (
              <li key={report.id}>
                <strong>제보 #{report.id}</strong>
                <span>{report.location}</span>
                <button type="button" className="text-button" onClick={() => {
                  setIsEmergencyOpen(false)
                  navigate(`/reports/${report.id}`)
                }}>
                  상세 보기
                </button>
              </li>
            ))}
          </ul>
        ) : (
          <p>현재 긴급 위험 알림이 없습니다.</p>
        )}
      </AdminModal>
    </header>
  )
}
