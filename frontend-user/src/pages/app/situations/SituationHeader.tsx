import { useLocation, useNavigate } from 'react-router'

type SituationNavigationState = {
  from?: 'notifications'
  returnTo?: string
  notificationFilter?: string
}

export function SituationHeader() {
  const navigate = useNavigate()
  const location = useLocation()
  const handleBack = () => {
    const navigationState = location.state as SituationNavigationState | null

    if (navigationState?.returnTo === '/app/notifications' || navigationState?.from === 'notifications') {
      navigate('/app/notifications', {
        replace: true,
        state: navigationState.notificationFilter
          ? { notificationFilter: navigationState.notificationFilter }
          : undefined,
      })
      return
    }

    navigate('/app', { replace: true })
  }

  return (
    <>
      <header className="situation-page-header">
        <button type="button" aria-label="뒤로가기" onClick={handleBack}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m15 5-7 7 7 7"/><path d="M8 12h12"/></svg></button>
        <h1>상황 확인</h1>
        <span aria-hidden="true" />
      </header>
      <section className="situation-intro">
        <p>현재 지역의 실시간 제보</p>
      </section>
    </>
  )
}
