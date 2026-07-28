import type { ReactNode } from 'react'
import { Navigate } from 'react-router'
import { ROUTE_PATHS } from '../router/paths'
import { useAuth } from './useAuth'

interface PublicOnlyRouteProps {
  children: ReactNode
}

export function PublicOnlyRoute({ children }: PublicOnlyRouteProps) {
  const { status, isAuthenticated } = useAuth()

  if (status === 'initializing') {
    return <p role="status">세션을 확인하고 있습니다...</p>
  }

  if (isAuthenticated) {
    return <Navigate to={ROUTE_PATHS.DASHBOARD} replace />
  }

  return children
}
