import { useEffect, type ReactNode } from 'react'
import { clearAccessToken } from '../../api/accessToken'
import { refreshSession } from '../../api/client'
import { USE_MOCK_API } from '../../api/mockMode'
import { subscribeToSessionExpired } from '../../api/sessionEvents'
import { useAuthStore } from '../../stores/authStore'
import { getCurrentUser } from './authApi'
import { mockCitizen } from './authMock'
import { canUseCitizenApp } from './authSession'

export function AuthBootstrap({ children }: { children: ReactNode }) {
  const authenticate = useAuthStore((state) => state.authenticate)
  const clearAuthentication = useAuthStore((state) => state.clearAuthentication)
  const finishInitialization = useAuthStore((state) => state.finishInitialization)

  useEffect(() => subscribeToSessionExpired(() => {
    clearAuthentication('세션이 만료되었습니다. 다시 로그인해 주세요.')
  }), [clearAuthentication])

  useEffect(() => {
    let active = true

    async function restoreSession() {
      // 목 모드에서는 백엔드 없이 바로 로그인된 상태로 진입한다.
      if (USE_MOCK_API) {
        if (active) authenticate(mockCitizen)
        return
      }

      try {
        await refreshSession()
        const user = await getCurrentUser()
        if (!canUseCitizenApp(user)) throw new Error('Citizen role required')
        if (active) authenticate(user)
      } catch {
        clearAccessToken()
        if (active) finishInitialization()
      }
    }

    void restoreSession()
    return () => { active = false }
  }, [authenticate, finishInitialization])

  return children
}
