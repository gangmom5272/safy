import type { ReactNode } from 'react'
import { Navigate } from 'react-router'
import { ROUTE_PATHS } from '../router/paths'
import { useAuth } from './useAuth'

interface PublicOnlyRouteProps {
  children: ReactNode
}

export function PublicOnlyRoute({ children }: PublicOnlyRouteProps) {
  const { isAuthenticated } = useAuth()

  if (isAuthenticated) {
    return <Navigate to={ROUTE_PATHS.DASHBOARD} replace />
  }

  return children
}
