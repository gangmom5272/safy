import { useLocation, useNavigate } from 'react-router'
import { useAuth } from '../../auth/useAuth'
import { ROUTE_PATHS } from '../../router/paths'

const pageTitleByPath: Record<string, string> = {
  [ROUTE_PATHS.DASHBOARD]: '대시보드',
  [ROUTE_PATHS.REPORTS]: '제보 관리',
  [ROUTE_PATHS.MONITORING]: '실시간 모니터링',
  [ROUTE_PATHS.RISK_MAP]: '위험도 지도',
  [ROUTE_PATHS.SETTINGS]: '시스템 설정',
}

function getPageTitle(pathname: string) {
  if (pathname.startsWith(ROUTE_PATHS.REPORTS)) {
    return pathname === ROUTE_PATHS.REPORTS ? '제보 관리' : '제보 상세'
  }

  return pageTitleByPath[pathname] ?? '관리자 페이지'
}

export function AdminHeader() {
  const { pathname } = useLocation()
  const navigate = useNavigate()
  const { adminEmail, logout } = useAuth()

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
        <label className="admin-search">
          <span className="sr-only">관리자 페이지 검색</span>
          <input type="search" placeholder="검색어를 입력하세요" />
        </label>
        <button type="button" className="icon-button" aria-label="알림 보기">
          !
        </button>
        <div className="admin-profile" aria-label="관리자 계정">
          {adminEmail ?? 'admin'}
        </div>
        <button type="button" className="logout-button" onClick={handleLogout}>
          로그아웃
        </button>
      </div>
    </header>
  )
}
