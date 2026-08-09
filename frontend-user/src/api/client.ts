import axios, { type InternalAxiosRequestConfig } from 'axios'
import { clearAccessToken, getAccessToken, setAccessToken } from './accessToken'
import { toApiRequestError } from './errors'
import { createRefreshCoordinator } from './refreshCoordinator'
import { notifySessionExpired } from './sessionEvents'
import type { ApiResponse } from './types'

interface TokenResponse {
  accessToken: string
}

interface RetryableRequestConfig extends InternalAxiosRequestConfig {
  _retry?: boolean
}

// API 주소를 이 한 곳에서만 정한다. 다른 모듈은 이 값을 가져다 쓴다.
// Vite는 .env를 빌드 시점에 읽어 문자열을 번들에 박아 넣으므로, .env가 저장소에
// 있으면 CI 빌드가 개발용 주소를 그대로 구워버린다(실제로 배포 번들에 localhost가
// 박혀 시민 앱이 서버에 붙지 못했다). 그래서 .env는 추적하지 않고 폴백을 둔다.
// 개발 서버에서는 로컬 백엔드로, 빌드 결과물은 같은 출처(nginx)로 향한다.
export const apiBaseUrl =
  import.meta.env.VITE_API_BASE_URL ||
  (import.meta.env.DEV ? 'http://localhost:8080/api/v1' : '/api/v1')

const clientOptions = {
  baseURL: apiBaseUrl,
  timeout: 10_000,
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
  },
}

const apiClient = axios.create(clientOptions)
const refreshClient = axios.create(clientOptions)

async function requestNewAccessToken() {
  const response = await refreshClient.post<ApiResponse<TokenResponse>>('/auth/token/refresh')
  const token = response.data.data.accessToken
  setAccessToken(token)
  return token
}

export const refreshSession = createRefreshCoordinator(requestNewAccessToken)

apiClient.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) config.headers.set('Authorization', `Bearer ${token}`)
  return config
})

apiClient.interceptors.response.use(
  (response) => response,
  async (error: unknown) => {
    if (!axios.isAxiosError(error) || !error.config) {
      return Promise.reject(toApiRequestError(error))
    }

    const config = error.config as RetryableRequestConfig
    const shouldRefresh = error.response?.status === 401
      && !config._retry
      && !config.url?.includes('/auth/')

    if (!shouldRefresh) return Promise.reject(toApiRequestError(error))

    config._retry = true

    try {
      const token = await refreshSession()
      config.headers.set('Authorization', `Bearer ${token}`)
      return await apiClient(config)
    } catch (refreshError) {
      clearAccessToken()
      notifySessionExpired()
      return Promise.reject(toApiRequestError(refreshError))
    }
  },
)

export default apiClient
