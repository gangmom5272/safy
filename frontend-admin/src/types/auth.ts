export type UserRole = 'CITIZEN' | 'OPERATOR' | 'SUPER_ADMIN'
export type UserStatus = 'ACTIVE' | 'SUSPENDED' | 'WITHDRAWN'

export interface AdminUser {
  id: number
  displayName: string
  role: UserRole
  status: UserStatus
  controlCenterId: number | null
  controlCenterName: string | null
}

export interface TokenResponse {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: number
  userId: number
  role: UserRole
  newUser: boolean
}

export interface LoginCredentials {
  loginId: string
  password: string
}

export type AuthStatus = 'initializing' | 'authenticated' | 'unauthenticated'

export interface AuthContextValue {
  status: AuthStatus
  isAuthenticated: boolean
  user: AdminUser | null
  adminName: string | null
  sessionMessage: string | null
  login: (credentials: LoginCredentials) => Promise<void>
  logout: () => Promise<void>
}
