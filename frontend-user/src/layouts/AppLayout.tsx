import { NavLink, Outlet, useLocation } from 'react-router'

function NotificationIcon() {
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9"/><path d="M10 21h4"/></svg>
}

export function AppLayout() {
  const { pathname } = useLocation()
  const isHome = pathname === '/app' || pathname === '/app/'
  const isNotifications = pathname === '/app/notifications'
  const isMyPage = pathname.startsWith('/app/mypage')
  const isSituationsMain = pathname === '/app/situations'
  const isReportDetail = pathname.startsWith('/app/situations/reports/') || pathname.startsWith('/app/reports/')
  const isReportCreate = pathname === '/app/report'

  return (
    <div className={`app-shell ${isHome ? 'app-shell--landing' : ''}`}>
      {!isHome && !isNotifications && !isMyPage && !isSituationsMain && !isReportDetail && !isReportCreate && <header className="app-header">
        <div className="app-brand"><strong>SAFY</strong><span>Safe Area For You</span></div>
        <NavLink className="app-header__notification" to="/app/notifications" aria-label="알림"><NotificationIcon /></NavLink>
      </header>}
      <main className={`app-main ${isHome ? 'app-main--landing' : ''}`}>
        <Outlet />
      </main>
    </div>
  )
}
