import apiClient from '../../api/client'
import { mockResponse, USE_MOCK_API } from '../../api/mockMode'
import { mockLiveSessions } from '../../mocks/adminApiMocks'
import type { ApiResponse } from '../../api/types'
import type { WebRTCConnection, WebRTCConnectionStatus } from '../../types/admin'

type BackendStreamStatus = 'CONNECTING' | 'LIVE' | 'DISCONNECTED' | 'ENDED'

export interface OperatorLiveSession {
  streamRoomId: string
  reportId: number
  status: BackendStreamStatus
  category: string
  subcategory: string
  description: string
  latitude: number | null
  longitude: number | null
  address: string | null
  startedAt: string | null
  endedAt: string | null
  expiresAt: string | null
  createdAt: string
}

export interface LiveViewerToken {
  streamRoomId: string
  serverUrl: string
  roomName: string
  participantIdentity: string
  participantType: string
  token: string
  expiresAt: string
}

// 백엔드 StreamStatus 를 관제 화면 연결 상태로 옮긴다.
const statusMap: Record<BackendStreamStatus, WebRTCConnectionStatus> = {
  CONNECTING: 'requested',
  LIVE: 'live',
  DISCONNECTED: 'waiting',
  ENDED: 'ended',
}

export function toWebRTCConnection(session: OperatorLiveSession): WebRTCConnection {
  return {
    id: session.streamRoomId,
    reportId: session.reportId,
    location: session.address ?? '위치 정보 없음',
    // 위험도·품질·AI 판정은 백엔드 라이브 세션 응답에 없다.
    riskLevel: null,
    riskScore: null,
    connectedAt: session.startedAt ?? session.createdAt,
    connectionStatus: statusMap[session.status] ?? 'ended',
    quality: null,
    aiDetected: false,
    aiAnalysisResult: session.description,
    confidence: null,
  }
}

export async function getLiveSessions(size = 50) {
  if (USE_MOCK_API) return mockResponse(mockLiveSessions().map(toWebRTCConnection))

  const [activeResponse, disconnectedResponse] = await Promise.all([
    apiClient.get<ApiResponse<{ content: OperatorLiveSession[] }>>(
      '/admin/stream-rooms',
      { params: { size } },
    ),
    apiClient.get<ApiResponse<{ content: OperatorLiveSession[] }>>(
      '/admin/stream-rooms',
      { params: { status: 'DISCONNECTED', size } },
    ),
  ])
  const sessions = [...activeResponse.data.data.content, ...disconnectedResponse.data.data.content]
  return [...new Map(sessions.map((session) => [session.streamRoomId, session])).values()]
    .map(toWebRTCConnection)
}

export async function getLiveSession(streamRoomId: string) {
  if (USE_MOCK_API) {
    const sessions = mockLiveSessions()
    const session = sessions.find((item) => item.streamRoomId === streamRoomId) ?? sessions[0]
    return mockResponse(toWebRTCConnection(session))
  }

  const response = await apiClient.get<ApiResponse<OperatorLiveSession>>(`/admin/stream-rooms/${streamRoomId}`)
  return toWebRTCConnection(response.data.data)
}

export async function issueViewerToken(streamRoomId: string) {
  const response = await apiClient.post<ApiResponse<LiveViewerToken>>(`/admin/stream-rooms/${streamRoomId}/tokens`)
  return response.data.data
}
