import type { ReactNode } from 'react'
import { Navigate } from 'react-router'
import { ROUTE_PATHS } from '../router/paths'
import type { UserRole } from '../types/auth'
import { useAuth } from './useAuth'

/** 역할별 첫 화면. SUPER_ADMIN 은 인사 관리, OPERATOR 는 관제 홈이다. */
function roleHomePath(role: UserRole | undefined) {
  return role === 'SUPER_ADMIN' ? ROUTE_PATHS.OPERATORS : ROUTE_PATHS.DASHBOARD
}

/** 로그인 직후·루트 진입 시 역할에 맞는 화면으로 보낸다. */
export function RoleHome() {
  const { user } = useAuth()
  return <Navigate to={roleHomePath(user?.role)} replace />
}

interface RequireRoleProps {
  role: UserRole
  children: ReactNode
}

/** 허용되지 않은 역할이 접근하면 자기 역할의 첫 화면으로 돌려보낸다. */
export function RequireRole({ role, children }: RequireRoleProps) {
  const { user } = useAuth()

  if (user?.role !== role) {
    return <Navigate to={roleHomePath(user?.role)} replace />
  }

  return children
}
