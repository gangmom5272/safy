import { useLocation, useNavigate } from 'react-router'
import { PageHeader } from '../../../components/common/PageHeader'

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
      <PageHeader title="상황 확인" onBack={handleBack} />
      <section className="situation-intro">
        <p>지도에서 지역 상황과 제보를 확인하세요</p>
      </section>
    </>
  )
}
