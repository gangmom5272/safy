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

export const apiBaseUrl = import.meta.env.VITE_API_BASE_URL
  || (import.meta.env.DEV ? 'http://localhost:8090/api/v1' : '/api/v1')

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
