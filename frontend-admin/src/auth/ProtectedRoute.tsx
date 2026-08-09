import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import { ROUTE_PATHS } from '../router/paths'
import { useAuth } from './useAuth'

interface ProtectedRouteProps {
  children: ReactNode
}

export function ProtectedRoute({ children }: ProtectedRouteProps) {
  const { status, isAuthenticated } = useAuth()
  const location = useLocation()

  if (status === 'initializing') {
    return <p role="status">세션을 확인하고 있습니다...</p>
  }

  if (!isAuthenticated) {
    return <Navigate to={ROUTE_PATHS.LOGIN} replace state={{ from: location.pathname }} />
  }

  return children
}
