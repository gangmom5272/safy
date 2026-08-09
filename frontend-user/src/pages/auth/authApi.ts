import { clearAccessToken, setAccessToken } from '../../api/accessToken'
import apiClient from '../../api/client'
import type { ApiResponse } from '../../api/types'
import type { TokenResponse, UserResponse } from './authSession'
import { disableWebPush } from '../app/notifications/webPush'

export async function exchangeSocialCode(code: string) {
  const response = await apiClient.post<ApiResponse<TokenResponse>>('/auth/social/exchange', { code })
  setAccessToken(response.data.data.accessToken)
  return response.data.data
}

export async function getCurrentUser() {
  const response = await apiClient.get<ApiResponse<UserResponse>>('/users/me')
  return response.data.data
}

export async function logout() {
  try {
    await disableWebPush().catch((error: unknown) => console.warn('Web Push unsubscribe failed', error))
    await apiClient.post('/auth/logout')
  } finally {
    clearAccessToken()
  }
}

export async function withdraw() {
  await apiClient.delete('/users/me')
  clearAccessToken()
}
