import { useLocation, useNavigate } from 'react-router'
import { PageHeader } from '../../../components/common/PageHeader'

type SituationNavigationState = {
  from?: 'notifications' | 'favorites'
  returnTo?: string
  notificationFilter?: string
}

export function SituationHeader() {
  const navigate = useNavigate()
  const location = useLocation()
  const handleBack = () => {
    const navigationState = location.state as SituationNavigationState | null

    if (navigationState?.from === 'favorites') {
      navigate(-1)
      return
    }

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
        <p>현재 지역의 실시간 제보</p>
      </section>
    </>
  )
}
