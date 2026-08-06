import { NavLink } from 'react-router'
import { useNotificationUnreadCount } from '../../pages/app/notifications/notificationUnread'
import './bottomNav.css'

function SearchIcon() {
  return <svg viewBox="0 0 48 48" fill="none" stroke="currentColor" strokeWidth="3.5" strokeLinecap="round" aria-hidden="true"><circle cx="21" cy="21" r="13"/><path d="m31 31 10 10"/></svg>
}

function ReportIcon() {
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M4 8h3l1.5-2h7L17 8h3a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9a1 1 0 0 1 1-1Z"/><circle cx="12" cy="13" r="3.5"/></svg>
}

function BellIcon() {
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9"/><path d="M10 21h4"/></svg>
}

function PersonIcon() {
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><circle cx="12" cy="8" r="3.5"/><path d="M5 20c0-3.5 3-6 7-6s7 2.5 7 6"/></svg>
}

function SettingsIcon() {
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1-2.8 2.8-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.6v.2h-4V21a1.7 1.7 0 0 0-1-1.6 1.7 1.7 0 0 0-1.9.3l-.1.1L4.2 17l.1-.1a1.7 1.7 0 0 0 .3-1.9A1.7 1.7 0 0 0 3 14H2.8v-4H3a1.7 1.7 0 0 0 1.6-1 1.7 1.7 0 0 0-.3-1.9L4.2 7 7 4.2l.1.1a1.7 1.7 0 0 0 1.9.3 1.7 1.7 0 0 0 1-1.6v-.2h4V3a1.7 1.7 0 0 0 1 1.6 1.7 1.7 0 0 0 1.9-.3l.1-.1L19.8 7l-.1.1a1.7 1.7 0 0 0-.3 1.9 1.7 1.7 0 0 0 1.6 1h.2v4H21a1.7 1.7 0 0 0-1.6 1Z"/></svg>
}

export function BottomNav() {
  const unreadCount = useNotificationUnreadCount()

  return (
    <nav className="bottom-nav" aria-label="주요 화면 이동">
      <NavLink to="/app/situations" className={({ isActive }) => `bottom-nav__item ${isActive ? 'active' : ''}`}>
        <SearchIcon />
        <span>실시간 지도</span>
      </NavLink>
      <NavLink to="/app/notifications" className={({ isActive }) => `bottom-nav__item ${isActive ? 'active' : ''}`} aria-label={unreadCount ? `알림, 읽지 않은 알림 ${unreadCount}개` : '알림'}>
        <span className="bottom-nav__icon-wrap">
          <BellIcon />
          {unreadCount > 0 && <span className="notification-unread-badge">{unreadCount > 99 ? '99+' : unreadCount}</span>}
        </span>
        <span>알림</span>
      </NavLink>
      <NavLink to="/app/report" className={({ isActive }) => `bottom-nav__item bottom-nav__item--report ${isActive ? 'active' : ''}`}>
        <span className="bottom-nav__report"><ReportIcon /></span>
        <span>제보</span>
      </NavLink>
      <NavLink to="/app/mypage" end className={({ isActive }) => `bottom-nav__item ${isActive ? 'active' : ''}`}>
        <PersonIcon />
        <span>내 정보</span>
      </NavLink>
      <NavLink to="/app/mypage/settings" className={({ isActive }) => `bottom-nav__item ${isActive ? 'active' : ''}`}>
        <SettingsIcon />
        <span>설정</span>
      </NavLink>
    </nav>
  )
}
