import { NavLink, Outlet } from 'react-router'

const appNavItems = [
  { to: '/app', label: '홈', end: true },
  { to: '/app/situations', label: '상황 확인' },
  { to: '/app/report', label: '제보하기' },
  { to: '/app/notifications', label: '알림' },
  { to: '/app/mypage', label: '마이페이지' },
]

export function AppLayout() {
  return (
    <div className="app-shell">
      <header className="app-header">
        <strong>SAFE:ON</strong>
        <span>사용자 앱</span>
      </header>
      <main className="app-main">
        <Outlet />
      </main>
      <nav className="app-bottom-nav" aria-label="사용자 주요 메뉴">
        {appNavItems.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.end}
            className={({ isActive }) => (isActive ? 'active' : undefined)}
          >
            {item.label}
          </NavLink>
        ))}
      </nav>
    </div>
  )
}
