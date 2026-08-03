import apiClient from '../../api/client'
import { mockResponse, USE_MOCK_API } from '../../api/mockMode'
import { mockQueueItems } from '../../mocks/adminApiMocks'
import type { ApiResponse, PageResponse } from '../../api/types'
import type { LiveStreamStatus } from '../../types/admin'

type QueueCategory = 'CROWD_CONGESTION' | 'ACCIDENT_RISK' | 'FLOOD_WEATHER' | 'FACILITY_ROAD' | 'OTHER'

interface QueueImageView {
  imageCount: number
  status: string
  thumbnailUrl: string | null
  thumbnailUrlExpiresAt: string | null
}

interface QueueLiveView {
  streamRoomId: string
  status: LiveStreamStatus
  startedAt: string | null
  expiresAt: string | null
}

interface QueueRiskView {
  status: string
  score: number | null
  /** 백엔드 RiskLevel 은 소문자로 직렬화된다(very_high 등). RiskBadge 가 그대로 읽는다. */
  level: string | null
  confidence: number
  confirmRequired: boolean
  assessedAt: string | null
}

export interface QueueItem {
  reportId: number
  reportType: 'IMAGE' | 'LIVE'
  category: QueueCategory
  subcategory: string | null
  description: string | null
  latitude: number | null
  longitude: number | null
  address: string | null
  reportedAt: string
  image: QueueImageView | null
  live: QueueLiveView | null
  risk: QueueRiskView | null
}

export const QUEUE_CATEGORY_LABELS: Record<QueueCategory, string> = {
  CROWD_CONGESTION: '인파',
  ACCIDENT_RISK: '사고 위험',
  FLOOD_WEATHER: '침수·기상',
  FACILITY_ROAD: '시설·도로',
  OTHER: '기타',
}

/**
 * 센터 공용 큐. 정렬은 서버가 오래된 순으로 고정한다(대시보드와 반대).
 * 방치가 길수록 위험하다는 판단이 들어간 것이라 클라이언트에서 다시 정렬하지 않는다.
 */
export async function getQueue(page = 0, size = 25) {
  if (USE_MOCK_API) return mockResponse(mockQueueItems())

  const response = await apiClient.get<ApiResponse<PageResponse<QueueItem>>>(
    '/operator/dashboard/reports/queue',
    { params: { page, size } },
  )
  return response.data.data.content
}

/** 큐의 제보를 내가 담당한다. 동시에 다른 담당자가 먼저 누르면 409 다. */
export async function claimReport(reportId: number) {
  if (USE_MOCK_API) return mockResponse(undefined)

  await apiClient.post(`/operator/reports/${reportId}/claim`)
}

/** 접수 후 경과 시간. 큐에서는 절대 시각보다 "얼마나 방치됐나"가 판단 기준이다. */
export function formatElapsed(reportedAt: string, now: number = Date.now()): string {
  const elapsedMs = now - new Date(reportedAt).getTime()
  if (!Number.isFinite(elapsedMs) || elapsedMs < 0) return '방금'

  const minutes = Math.floor(elapsedMs / 60_000)
  if (minutes < 1) return '방금'
  if (minutes < 60) return `${minutes}분 경과`

  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}시간 ${minutes % 60}분 경과`

  return `${Math.floor(hours / 24)}일 ${hours % 24}시간 경과`
}
