import apiClient from '../../api/client'
import type { ApiResponse } from '../../api/types'

// 서울시 실시간 도시데이터 기반 공공 정보. 인증 없는 공개 API 다.
// 좌표가 아직 지오코딩되지 않은 항목이 있으므로 지도에 찍는 쪽에서 좌표 없는 항목을 건너뛴다.

export type CongestionLevel = 'RELAXED' | 'NORMAL' | 'SLIGHTLY_CROWDED' | 'CROWDED'

export interface PlaceCongestion {
  placeId: number
  externalPlaceId: string
  placeName: string
  category: string
  latitude: number | null
  longitude: number | null
  congestionLevel: CongestionLevel
  congestionMessage: string | null
  populationMin: number | null
  populationMax: number | null
  observedAt: string
}

export interface CulturalEvent {
  eventId: number
  title: string
  description: string | null
  venueName: string | null
  eventPeriod: string | null
  startDate: string | null
  endDate: string | null
  latitude: number | null
  longitude: number | null
  areaName: string | null
  paid: boolean | null
  thumbnailUrl: string | null
  detailUrl: string | null
}

/** 서울시 실시간 도시데이터의 혼잡도 4단계. 지도 마커와 상세 패널이 같은 색을 쓴다. */
export const CONGESTION_LEVEL_CONFIG: Record<CongestionLevel, { label: string; color: string }> = {
  RELAXED: { label: '여유', color: '#16a34a' },
  NORMAL: { label: '보통', color: '#eab308' },
  SLIGHTLY_CROWDED: { label: '약간 붐빔', color: '#f97316' },
  CROWDED: { label: '붐빔', color: '#dc2626' },
}

export const PLACE_CATEGORY_LABELS: Record<string, string> = {
  TOURIST_ZONE: '관광특구',
  HERITAGE: '고궁·문화유산',
  POPULATION_DENSE: '인구밀집지역',
  COMMERCIAL: '발달상권',
  PARK: '공원',
}

export function congestionConfig(level: string | null | undefined) {
  if (!level) return null
  return CONGESTION_LEVEL_CONFIG[level as CongestionLevel] ?? null
}

/** 서울시 주요 121장소의 최신 인구 밀집 상태. */
export async function getPlaceCongestions() {
  const response = await apiClient.get<ApiResponse<PlaceCongestion[]>>('/public-data/congestion')
  return response.data.data
}

/** 오늘 기준 진행 중인 문화 행사. */
export async function getOngoingEvents() {
  const response = await apiClient.get<ApiResponse<CulturalEvent[]>>('/public-data/events')
  return response.data.data
}
