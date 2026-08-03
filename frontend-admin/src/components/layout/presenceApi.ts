import apiClient from '../../api/client'
import { mockResponse, USE_MOCK_API } from '../../api/mockMode'
import type { ApiResponse } from '../../api/types'

export type PresenceStatus = 'ON_DUTY' | 'AWAY' | 'OFFLINE'

export interface OperatorPresence {
  status: PresenceStatus
  lastSeenAt: string | null
}

export const PRESENCE_LABELS: Record<PresenceStatus, string> = {
  ON_DUTY: '근무중',
  AWAY: '자리비움',
  OFFLINE: '오프라인',
}

/**
 * 담당자가 직접 고를 수 있는 상태. OFFLINE 은 넣지 않는다.
 * 서버가 접속 신호로 자동 판정하는 값이고, 직접 고르면 확인하지 않은 배정이 즉시 회수된다.
 */
export const SELECTABLE_PRESENCE: PresenceStatus[] = ['ON_DUTY', 'AWAY']

export async function getMyPresence() {
  if (USE_MOCK_API) return mockResponse<OperatorPresence>({ status: 'ON_DUTY', lastSeenAt: null })

  const response = await apiClient.get<ApiResponse<OperatorPresence>>('/operator/presence')
  return response.data.data
}

export async function changeMyPresence(status: PresenceStatus) {
  if (USE_MOCK_API) return mockResponse<OperatorPresence>({ status, lastSeenAt: null })

  const response = await apiClient.patch<ApiResponse<OperatorPresence>>('/operator/presence', { status })
  return response.data.data
}
