import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { AdminModal } from '../admin/AdminModal'
import { useAuth } from '../../auth/useAuth'
import { OperatorPresenceToggle } from './OperatorPresenceToggle'
import { ROUTE_PATHS } from '../../router/paths'
import { isEmergencyRisk } from '../../utils/adminRisk'
import { formatSeoulDateTime } from '../../utils/adminDate'
import { isNewReport } from '../../utils/adminVerification'
import type { AdminReport } from '../../types/admin'

const pageTitleByPath: Record<string, string> = {
  [ROUTE_PATHS.DASHBOARD]: '홈',
  [ROUTE_PATHS.REPORTS]: '제보 목록',
  [ROUTE_PATHS.QUEUE]: '미배정',
  [ROUTE_PATHS.MONITORING]: '실시간 모니터링',
  [ROUTE_PATHS.RISK_MAP]: '위험도 지도',
  [ROUTE_PATHS.SETTINGS]: '계정 설정',
  [ROUTE_PATHS.OPERATORS]: '담당자 관리',
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
  reports: AdminReport[]
  onCheckReport: (reportId: number) => void
}

export function AdminHeader({ reports, onCheckReport }: AdminHeaderProps) {
  const { pathname } = useLocation()
  const navigate = useNavigate()
  const { user } = useAuth()
  const [isEmergencyOpen, setIsEmergencyOpen] = useState(false)
  const [currentTime, setCurrentTime] = useState(() => new Date())
  const dangerReports = reports.filter((report) => (
    isNewReport(report) && isEmergencyRisk(report.riskLevel)
  ))
  const isDashboard = pathname === ROUTE_PATHS.DASHBOARD
  // 긴급 위험 알림은 관제(OPERATOR) 업무라 인사 관리만 하는 SUPER_ADMIN 에게는 숨긴다.
  const isOperator = user?.role === 'OPERATOR'

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
          <div className="admin-header__status" aria-label="현재 시간">
            <time dateTime={currentTime.toISOString()}>{formatSeoulDateTime(currentTime)}</time>
          </div>
        )}
      </div>
      <div className="admin-header__actions">
        <OperatorPresenceToggle />
        {isOperator && (
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
        )}
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
        <p className="emergency-alert-message">
          AI가 위험 또는 매우 위험으로 판정한 미확인 제보입니다.
        </p>
        {dangerReports.length > 0 ? (
          <ul className="emergency-report-list">
            {dangerReports.map((report) => (
              <li key={report.id}>
                <strong>제보 #{report.id}</strong>
                <span>{report.location}</span>
                <button type="button" className="text-button" onClick={() => {
                  onCheckReport(report.id)
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
