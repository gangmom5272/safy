import { useCallback, useEffect, useMemo, useState } from 'react'
import { clearAccessToken } from '../api/accessToken'
import { refreshSession } from '../api/client'
import { USE_MOCK_API } from '../api/mockMode'
import { subscribeToSessionExpired } from '../api/sessionEvents'
import { AuthContext } from './authContextValue'
import { mockOperator } from './authMock'
import { adminLogin, canUseAdminConsole, getCurrentUser, logout as requestLogout } from './authApi'
import type { AdminUser, AuthContextValue, AuthStatus } from '../types/auth'

const FORBIDDEN_MESSAGE = '관리자 권한이 없는 계정입니다.'

interface AuthProviderProps {
  children: React.ReactNode
}

export function AuthProvider({ children }: AuthProviderProps) {
  const [status, setStatus] = useState<AuthStatus>('initializing')
  const [user, setUser] = useState<AdminUser | null>(null)
  const [sessionMessage, setSessionMessage] = useState<string | null>(null)

  useEffect(() => subscribeToSessionExpired(() => {
    setStatus('unauthenticated')
    setUser(null)
    setSessionMessage('세션이 만료되었습니다. 다시 로그인해 주세요.')
  }), [])

  // 새로고침 시 HttpOnly Refresh Token 쿠키로 세션을 복구한다.
  useEffect(() => {
    let active = true

    async function restoreSession() {
      // 목 모드에서는 백엔드 없이 바로 로그인된 상태로 진입한다.
      if (USE_MOCK_API) {
        if (active) {
          setUser(mockOperator)
          setStatus('authenticated')
        }
        return
      }

      try {
        await refreshSession()
        const currentUser = await getCurrentUser()
        if (!canUseAdminConsole(currentUser)) throw new Error(FORBIDDEN_MESSAGE)
        if (!active) return
        setUser(currentUser)
        setStatus('authenticated')
      } catch {
        clearAccessToken()
        if (!active) return
        setUser(null)
        setStatus('unauthenticated')
      }
    }

    void restoreSession()
    return () => { active = false }
  }, [])

  const login = useCallback<AuthContextValue['login']>(async ({ loginId, password }) => {
    if (USE_MOCK_API) {
      setUser(mockOperator)
      setStatus('authenticated')
      setSessionMessage(null)
      return
    }

    await adminLogin(loginId, password)
    const currentUser = await getCurrentUser()

    if (!canUseAdminConsole(currentUser)) {
      clearAccessToken()
      throw new Error(FORBIDDEN_MESSAGE)
    }

    setUser(currentUser)
    setStatus('authenticated')
    setSessionMessage(null)
  }, [])

  const logout = useCallback(async () => {
    try {
      if (!USE_MOCK_API) await requestLogout()
    } finally {
      setUser(null)
      setStatus('unauthenticated')
      setSessionMessage(null)
    }
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({
      status,
      isAuthenticated: status === 'authenticated',
      user,
      adminName: user?.displayName ?? null,
      sessionMessage,
      login,
      logout,
    }),
    [status, user, sessionMessage, login, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
