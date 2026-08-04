import { clearAccessToken, setAccessToken } from '../api/accessToken'
import apiClient from '../api/client'
import type { ApiResponse } from '../api/types'
import type { AdminUser, TokenResponse } from '../types/auth'

export async function adminLogin(loginId: string, password: string) {
  const response = await apiClient.post<ApiResponse<TokenResponse>>('/auth/admin/login', { loginId, password })
  setAccessToken(response.data.data.accessToken)
  return response.data.data
}

export async function getCurrentUser() {
  const response = await apiClient.get<ApiResponse<AdminUser>>('/users/me')
  return response.data.data
}

export async function logout() {
  try {
    // 관리자 전용 쿠키만 지운다. 같은 브라우저의 시민 앱 세션은 그대로 유지된다.
    await apiClient.post('/auth/admin/logout')
  } finally {
    clearAccessToken()
  }
}

export function canUseAdminConsole(user: Pick<AdminUser, 'role' | 'status'>) {
  return (user.role === 'OPERATOR' || user.role === 'SUPER_ADMIN') && user.status === 'ACTIVE'
}
