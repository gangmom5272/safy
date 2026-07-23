import { useMemo, useState } from 'react'
import {
  clearAdminSession,
  readAdminSession,
  saveAdminSession,
} from './authStorage'
import { AuthContext } from './authContextValue'
import type { AdminSession, AuthContextValue, LoginCredentials } from '../types/auth'

// Mock account for local UI flow validation only. Replace this check with the backend auth API later.
const MOCK_ADMIN_CREDENTIALS: LoginCredentials = {
  email: 'admin@safy.kr',
  password: 'safy1234',
}

interface AuthProviderProps {
  children: React.ReactNode
}

export function AuthProvider({ children }: AuthProviderProps) {
  const [session, setSession] = useState<AdminSession | null>(() => readAdminSession())

  const value = useMemo<AuthContextValue>(
    () => ({
      isAuthenticated: session?.isAuthenticated === true,
      adminEmail: session?.email ?? null,
      login: (credentials) => {
        const isValidMockAccount =
          credentials.email === MOCK_ADMIN_CREDENTIALS.email &&
          credentials.password === MOCK_ADMIN_CREDENTIALS.password

        if (!isValidMockAccount) {
          return false
        }

        const nextSession: AdminSession = {
          isAuthenticated: true,
          email: credentials.email,
        }

        saveAdminSession(nextSession)
        setSession(nextSession)
        return true
      },
      logout: () => {
        clearAdminSession()
        setSession(null)
      },
    }),
    [session],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
