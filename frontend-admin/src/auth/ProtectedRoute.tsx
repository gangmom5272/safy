import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import { ROUTE_PATHS } from '../router/paths'
import { useAuth } from './useAuth'

interface ProtectedRouteProps {
  children: ReactNode
}

export function ProtectedRoute({ children }: ProtectedRouteProps) {
  const { isAuthenticated } = useAuth()
  const location = useLocation()

  if (!isAuthenticated) {
    return <Navigate to={ROUTE_PATHS.LOGIN} replace state={{ from: location.pathname }} />
  }

  return children
}
