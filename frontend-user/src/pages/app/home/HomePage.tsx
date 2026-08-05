import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { SafyLogo } from '../../../components/SafyLogo'
import { useNotificationUnreadCount } from '../notifications/notificationUnread'
import './home.css'

const HOME_INTRO_KEY = 'safy-home-intro-completed'
const INTRO_HOLD_DURATION = 1400

type HomeActionButtonProps = {
  label: string
  variant: 'primary' | 'secondary'
  icon: 'search' | 'report'
  onClick: () => void
}

function ActionIcon({ name }: { name: HomeActionButtonProps['icon'] }) {
  if (name === 'search') {
    return <svg viewBox="0 0 48 48" fill="none" stroke="currentColor" strokeWidth="3.5" strokeLinecap="round" aria-hidden="true"><circle cx="21" cy="21" r="13"/><path d="m31 31 10 10"/></svg>
  }
  return <svg viewBox="0 0 48 48" fill="none" stroke="currentColor" strokeWidth="3.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M27 9H11a4 4 0 0 0-4 4v24a4 4 0 0 0 4 4h24a4 4 0 0 0 4-4V21"/><path d="m19 29 2-8L36 6a4 4 0 0 1 6 6L27 27Z"/><path d="m33 9 6 6"/></svg>
}

function HomeActionButton({ label, variant, icon, onClick }: HomeActionButtonProps) {
  return <button className={`home-action home-action--${variant}`} type="button" onClick={onClick}><ActionIcon name={icon} /><span>{label}</span></button>
}

function QuickAccessIcon({ name }: { name: 'notification' | 'menu' }) {
  if (name === 'notification') {
    return <svg viewBox="0 0 32 32" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M24 13a8 8 0 0 0-16 0c0 9-4 9-4 12h24c0-3-4-3-4-12Z"/><path d="M13 28h6"/><path d="M14 4a2 2 0 0 1 4 0"/></svg>
  }
  return <svg viewBox="0 0 32 32" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" aria-hidden="true"><path d="M5 8h22M5 16h22M5 24h22"/></svg>
}

export function HomePage() {
  const navigate = useNavigate()
  const unreadCount = useNotificationUnreadCount()
  const [expanded, setExpanded] = useState(() =>
    sessionStorage.getItem(HOME_INTRO_KEY) === 'true'
    || window.matchMedia('(prefers-reduced-motion: reduce)').matches,
  )

  useEffect(() => {
    if (expanded) return

    const timer = window.setTimeout(() => {
      sessionStorage.setItem(HOME_INTRO_KEY, 'true')
      setExpanded(true)
    }, INTRO_HOLD_DURATION)

    return () => window.clearTimeout(timer)
  }, [expanded])

  const showHomeActions = expanded

  return (
    <div className={`landing-home ${expanded ? 'landing-home--expanded' : 'landing-home--intro'}`}>
      <nav className={`home-quick-access ${showHomeActions ? 'home-quick-access--visible' : ''}`} aria-label="빠른 접근" aria-hidden={!showHomeActions}>
        <button type="button" aria-label={unreadCount ? `알림, 읽지 않은 알림 ${unreadCount}개` : '알림'} tabIndex={showHomeActions ? 0 : -1} onClick={() => navigate('/app/notifications')}><QuickAccessIcon name="notification" />{unreadCount > 0 && <span className="notification-unread-badge">{unreadCount > 99 ? '99+' : unreadCount}</span>}</button>
        <button type="button" aria-label="마이페이지" tabIndex={showHomeActions ? 0 : -1} onClick={() => navigate('/app/mypage')}><QuickAccessIcon name="menu" /></button>
      </nav>

      <div className="landing-home__content">
        <section className="home-identity" aria-labelledby="home-brand-title">
          <SafyLogo className="home-logo" />
          <h1 id="home-brand-title">SAFY</h1>
          <p>Safe Area For You</p>
        </section>

        <div className="home-actions" aria-label="주요 서비스" aria-hidden={!showHomeActions}>
          <HomeActionButton label="검색하기" icon="search" variant="primary" onClick={() => navigate('/app/situations')} />
          <HomeActionButton label="제보하기" icon="report" variant="secondary" onClick={() => navigate('/app/report')} />
        </div>
      </div>
    </div>
  )
}
