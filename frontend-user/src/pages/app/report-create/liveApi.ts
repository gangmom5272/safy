import apiClient from '../../../api/client'
import { mockResponse, USE_MOCK_API } from '../../../api/mockMode'
import type { ApiResponse } from '../../../api/types'

export type LiveSessionStatus = 'CONNECTING' | 'LIVE' | 'DISCONNECTED' | 'ENDED'

export interface LiveSession {
  streamRoomId: string
  reportId: number
  status: LiveSessionStatus
  startedAt: string | null
  endedAt: string | null
  expiresAt: string | null
}

export interface LiveToken {
  streamRoomId: string
  serverUrl: string
  roomName: string
  participantIdentity: string
  participantType: string
  token: string
  expiresAt: string
}

function mockSession(reportId: number, streamRoomId = `mock-room-${reportId}`): LiveSession {
  return {
    streamRoomId,
    reportId,
    status: 'LIVE',
    startedAt: new Date().toISOString(),
    endedAt: null,
    expiresAt: null,
  }
}

export async function createLiveSession(reportId: number) {
  if (USE_MOCK_API) return mockResponse(mockSession(reportId))

  const response = await apiClient.post<ApiResponse<LiveSession>>(`/reports/${reportId}/live-session`)
  return response.data.data
}

export async function issueReporterToken(streamRoomId: string) {
  if (USE_MOCK_API) {
    return mockResponse<LiveToken>({
      streamRoomId,
      serverUrl: '',
      roomName: streamRoomId,
      participantIdentity: 'mock-reporter',
      participantType: 'REPORTER',
      token: 'mock-token',
      expiresAt: new Date(Date.now() + 600_000).toISOString(),
    })
  }

  const response = await apiClient.post<ApiResponse<LiveToken>>(
    `/stream-rooms/${streamRoomId}/tokens`,
    { participantType: 'REPORTER' },
  )
  return response.data.data
}

export async function getLiveSession(streamRoomId: string) {
  if (USE_MOCK_API) return mockResponse(mockSession(0, streamRoomId))

  const response = await apiClient.get<ApiResponse<LiveSession>>(`/stream-rooms/${streamRoomId}`)
  return response.data.data
}

export async function endLiveSession(streamRoomId: string) {
  if (USE_MOCK_API) return mockResponse(undefined)

  await apiClient.patch(`/stream-rooms/${streamRoomId}/end`)
}
