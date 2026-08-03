import { useEffect, useState, type FormEvent } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { AdminModal } from '../admin/AdminModal'
import { adminReports, emergencyBanner } from '../../mocks/adminMockData'
import { ROUTE_PATHS } from '../../router/paths'
import { isEmergencyRisk } from '../../utils/adminRisk'
import { formatLastUpdated, formatSeoulDateTime } from '../../utils/adminDate'

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

interface AdminHeaderProps {
  lastDashboardUpdatedAt: number | null
}

function SearchIcon() {
  return (
    <svg className="admin-header__search-icon" viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false">
      <circle cx="11" cy="11" r="7" />
      <path d="m16 16 4 4" />
    </svg>
  )
}

export function AdminHeader({ lastDashboardUpdatedAt }: AdminHeaderProps) {
  const { pathname } = useLocation()
  const navigate = useNavigate()
  const [isEmergencyOpen, setIsEmergencyOpen] = useState(false)
  const [currentTime, setCurrentTime] = useState(() => new Date())
  const [searchQuery, setSearchQuery] = useState('')
  const dangerReports = adminReports.filter((report) => isEmergencyRisk(report.riskLevel))
  const isDashboard = pathname === ROUTE_PATHS.DASHBOARD

  const handleSearch = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const normalizedQuery = searchQuery.trim()
    if (!normalizedQuery) return

    setSearchQuery(normalizedQuery)
    // TODO: 관리자 전체 검색 API 또는 검색 결과 페이지와 연결
  }

  useEffect(() => {
    if (!isDashboard) return

    const updateCurrentTime = () => setCurrentTime(new Date())
    const initialUpdate = window.setTimeout(updateCurrentTime, 0)
    const timer = window.setInterval(updateCurrentTime, 1000)
    return () => {
      window.clearTimeout(initialUpdate)
      window.clearInterval(timer)
    }
  }, [isDashboard])

  return (
    <header className="admin-header">
      <div className="admin-header__left">
        <h1>{getPageTitle(pathname)}</h1>
        {isDashboard && (
          <div className="admin-header__status" aria-label="대시보드 갱신 정보">
            <time dateTime={currentTime.toISOString()}>{formatSeoulDateTime(currentTime)}</time>
            <span className="admin-header__divider" aria-hidden="true" />
            <span className="admin-header__updated">
              {formatLastUpdated(lastDashboardUpdatedAt, currentTime.getTime())}
            </span>
          </div>
        )}
      </div>
      <div className="admin-header__actions">
        {isDashboard && (
          <form className="admin-header__search" role="search" onSubmit={handleSearch}>
            <button type="submit" aria-label="전체 검색 실행">
              <SearchIcon />
            </button>
            <input
              type="search"
              value={searchQuery}
              onChange={(event) => setSearchQuery(event.target.value)}
              placeholder="전체 검색 (지역 · 제보 ID · 담당자)"
              aria-label="관리자 전체 검색"
            />
          </form>
        )}
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
