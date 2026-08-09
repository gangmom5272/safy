export type UserRole = 'CITIZEN' | 'OPERATOR' | 'SUPER_ADMIN'
export type UserStatus = 'ACTIVE' | 'SUSPENDED' | 'WITHDRAWN'

export interface UserResponse {
  id: number
  displayName: string
  role: UserRole
  status: UserStatus
  // 소셜 로그인으로 받은 값이라 소셜 계정이 없으면 null 이다.
  email: string | null
  profileImageUrl: string | null
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
