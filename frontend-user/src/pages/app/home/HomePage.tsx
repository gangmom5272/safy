import { useNavigate } from 'react-router'
import './home.css'

type HomeActionButtonProps = {
  label: string
  variant: 'primary' | 'secondary'
  icon: 'search' | 'report'
  onClick: () => void
}

function SafyLogo() {
  return (
    <div className="home-logo" aria-hidden="true">
      <svg viewBox="0 0 96 108" fill="none">
        <path d="M48 4C61 13 74 16 88 19v34c0 25-14 42-40 51C22 95 8 78 8 53V19C22 16 35 13 48 4Z" fill="currentColor" />
        <path d="M48 23c-8 5-15 7-25 9v22c0 4 .4 8 1.7 11.5H43c7.7 0 13.8 6.2 13.8 13.8V87C68.3 80.4 73 70 73 54V32c-9.8-2-17-4-25-9Z" fill="#fff" />
        <path d="M48 23v22c0 7.7 6.2 14 14 14h10.4C70.7 71.3 63.7 80 48 87V66.4c0-7.7-6.2-14-14-14H23V32c10-2 17-4 25-9Z" fill="#fff" fillOpacity=".95" />
      </svg>
    </div>
  )
}

function ActionIcon({ name }: { name: HomeActionButtonProps['icon'] }) {
  if (name === 'search') {
    return <svg viewBox="0 0 48 48" fill="none" stroke="currentColor" strokeWidth="3.5" strokeLinecap="round" aria-hidden="true"><circle cx="21" cy="21" r="13"/><path d="m31 31 10 10"/></svg>
  }

  return <svg viewBox="0 0 48 48" fill="none" stroke="currentColor" strokeWidth="3.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M27 9H11a4 4 0 0 0-4 4v24a4 4 0 0 0 4 4h24a4 4 0 0 0 4-4V21"/><path d="m19 29 2-8L36 6a4 4 0 0 1 6 6L27 27Z"/><path d="m33 9 6 6"/></svg>
}

function HomeActionButton({ label, variant, icon, onClick }: HomeActionButtonProps) {
  return (
    <button className={`home-action home-action--${variant}`} type="button" onClick={onClick}>
      <ActionIcon name={icon} />
      <span>{label}</span>
    </button>
  )
}

function QuickAccessIcon({ name }: { name: 'notification' | 'menu' }) {
  if (name === 'notification') {
    return <svg viewBox="0 0 32 32" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M24 13a8 8 0 0 0-16 0c0 9-4 9-4 12h24c0-3-4-3-4-12Z"/><path d="M13 28h6"/><path d="M14 4a2 2 0 0 1 4 0"/></svg>
  }

  return <svg viewBox="0 0 32 32" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" aria-hidden="true"><path d="M5 8h22M5 16h22M5 24h22"/></svg>
}

export function HomePage() {
  const navigate = useNavigate()

  return (
    <div className="landing-home">
      <nav className="home-quick-access" aria-label="빠른 접근">
        <button type="button" aria-label="알림" onClick={() => navigate('/app/notifications')}><QuickAccessIcon name="notification" /></button>
        <button type="button" aria-label="메뉴" onClick={() => navigate('/app/mypage')}><QuickAccessIcon name="menu" /></button>
      </nav>

      <div className="landing-home__content">
        <section className="home-identity" aria-labelledby="home-brand-title">
          <SafyLogo />
          <h1 id="home-brand-title">SAFY</h1>
          <p>Safe Area For You</p>
        </section>

        <div className="home-actions" aria-label="주요 서비스">
          <HomeActionButton label="검색하기" icon="search" variant="primary" onClick={() => navigate('/app/situations')} />
          <HomeActionButton label="제보하기" icon="report" variant="secondary" onClick={() => navigate('/app/report')} />
        </div>
      </div>
    </div>
  )
}
