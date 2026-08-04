import apiClient from '../../api/client'
import type { ApiResponse } from '../../api/types'

// 계약: backend/docs/grid-risk-api-contract.md
// 응답에 없는 칸은 근거가 없는 칸이므로 화면에서 색칠하지 않는다(0점으로 채우지 않는다).
// gridCode 는 추이 조회 식별자로만 쓰고, 그리는 데는 응답의 경계 좌표를 그대로 쓴다.

export interface GridRisk {
  gridCode: string
  resolutionMeters: number
  southWestLatitude: number
  southWestLongitude: number
  northEastLatitude: number
  northEastLongitude: number
  score: number
  /** very_low | low | medium | high | very_high — adminRisk 의 RiskLevel 과 같은 소문자 값 */
  congestionLevel: string
  /** REPORTED | PUBLIC_DATA. 공공데이터만 있는 칸은 medium(60점)을 넘지 않는다 */
  observationStatus: 'REPORTED' | 'PUBLIC_DATA'
  evidenceCount: number
  distinctReporterCount: number
  confidenceScore: number
  windowEndedAt: string
}

export interface GridRiskTrendPoint {
  windowEndedAt: string
  /** null 이면 그 시점에 위험이 끝났다는 기록이다. 그 구간은 그리지 않는다 */
  score: number | null
  congestionLevel: string | null
  observationStatus: string | null
}

export interface GridBounds {
  minLat: number
  maxLat: number
  minLng: number
  maxLng: number
}

// 격자 산식(§7). 요청 bbox 스냅에만 쓰고, 칸을 그리는 데는 응답의 경계를 쓴다.
const ORIGIN_LATITUDE = 37.4
const ORIGIN_LONGITUDE = 126.7
const STEP_LATITUDE = 0.0009
const STEP_LONGITUDE = 0.00113
/** 서버 검증 상한(위도 0.5도/경도 0.7도). 넘겨 보내면 400 GRID_002 다. */
const MAX_LATITUDE_SPAN = 0.5
const MAX_LONGITUDE_SPAN = 0.7

/** 카카오맵 레벨 → 서버 resolution. 줌 아웃 병합은 서버가 하고 화면에서 다시 묶지 않는다. */
export function resolutionForLevel(level: number) {
  if (level <= 4) return 1
  if (level === 5) return 2
  if (level === 6) return 4
  if (level === 7) return 8
  return 16
}

function snapDown(value: number, origin: number, step: number) {
  return origin + Math.floor((value - origin) / step) * step
}

function snapUp(value: number, origin: number, step: number) {
  return origin + Math.ceil((value - origin) / step) * step
}

/**
 * 요청 bbox 를 격자 경계로 스냅한다. 지도가 주는 실수 좌표를 그대로 보내면 1픽셀만
 * 움직여도 URL 이 달라져 브라우저 캐시(3분)가 전혀 듣지 않는다.
 */
export function snapBounds(bounds: GridBounds, resolution: number): GridBounds {
  const latStep = STEP_LATITUDE * resolution
  const lngStep = STEP_LONGITUDE * resolution

  let minLat = snapDown(bounds.minLat, ORIGIN_LATITUDE, latStep)
  let maxLat = snapUp(bounds.maxLat, ORIGIN_LATITUDE, latStep)
  let minLng = snapDown(bounds.minLng, ORIGIN_LONGITUDE, lngStep)
  let maxLng = snapUp(bounds.maxLng, ORIGIN_LONGITUDE, lngStep)

  // 스냅으로 넓어진 범위가 서버 상한을 넘으면 400 이므로 중심 기준으로 잘라낸다.
  if (maxLat - minLat > MAX_LATITUDE_SPAN) {
    const center = (minLat + maxLat) / 2
    minLat = center - MAX_LATITUDE_SPAN / 2
    maxLat = center + MAX_LATITUDE_SPAN / 2
  }
  if (maxLng - minLng > MAX_LONGITUDE_SPAN) {
    const center = (minLng + maxLng) / 2
    minLng = center - MAX_LONGITUDE_SPAN / 2
    maxLng = center + MAX_LONGITUDE_SPAN / 2
  }

  return { minLat, maxLat, minLng, maxLng }
}

/** URL 캐시 키가 부동소수 오차로 흔들리지 않게 자릿수를 고정한다. */
function fixed(value: number) {
  return value.toFixed(7)
}

export async function getGridRisks(bounds: GridBounds, resolution: number, signal?: AbortSignal) {
  const response = await apiClient.get<ApiResponse<GridRisk[]>>('/grid-risks', {
    params: {
      minLat: fixed(bounds.minLat),
      maxLat: fixed(bounds.maxLat),
      minLng: fixed(bounds.minLng),
      maxLng: fixed(bounds.maxLng),
      resolution,
    },
    signal,
  })
  return response.data.data
}

export async function getGridRiskTrend(gridCode: string, hours = 6) {
  const response = await apiClient.get<ApiResponse<GridRiskTrendPoint[]>>(
    `/grid-risks/${encodeURIComponent(gridCode)}/trend`,
    { params: { hours } },
  )
  return response.data.data
}
