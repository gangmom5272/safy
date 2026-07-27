export type UserRole = 'CITIZEN' | 'OPERATOR' | 'SUPER_ADMIN'
export type UserStatus = 'ACTIVE' | 'SUSPENDED' | 'WITHDRAWN'

export interface UserResponse {
  id: number
  displayName: string
  role: UserRole
  status: UserStatus
  createdAt: string
  updatedAt: string
}

export interface TokenResponse {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: number
  userId: number
  role: UserRole
  newUser: boolean
}

export function canUseCitizenApp(user: Pick<UserResponse, 'role' | 'status'>) {
  return user.role === 'CITIZEN' && user.status === 'ACTIVE'
}
