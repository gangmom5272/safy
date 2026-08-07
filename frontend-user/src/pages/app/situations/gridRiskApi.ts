import apiClient from '../../../api/client'
import type { ApiResponse } from '../../../api/types'

// 계약: backend/docs/grid-risk-api-contract.md (인증 불필요 공개 API)
// 응답에 없는 칸은 근거가 없는 칸이므로 색칠하지 않는다(0점으로 채우지 않는다).
// 칸을 그릴 때는 응답의 경계 좌표를 그대로 쓰고 gridCode 를 역산하지 않는다.

export interface GridRisk {
  gridCode: string
  resolutionMeters: number
  southWestLatitude: number
  southWestLongitude: number
  northEastLatitude: number
  northEastLongitude: number
  score: number
  /** very_low | low | medium | high | very_high (소문자) */
  congestionLevel: string
  observationStatus: 'REPORTED' | 'PUBLIC_DATA'
  evidenceCount: number
  distinctReporterCount: number
  confidenceScore: number
  windowEndedAt: string
}

export interface GridBounds {
  minLat: number
  maxLat: number
  minLng: number
  maxLng: number
}

// 격자 산식(계약 §7). 요청 bbox 스냅에만 쓴다.
const ORIGIN_LATITUDE = 37.4
const ORIGIN_LONGITUDE = 126.7
const STEP_LATITUDE = 0.0009
const STEP_LONGITUDE = 0.00113
// 서버 검증 상한(위도 0.5도/경도 0.7도). 넘겨 보내면 400 GRID_002 다.
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

/** 요청 bbox 를 격자 경계로 스냅해 같은 칸 범위를 보는 동안 URL 이 같아지게 한다(응답 캐시). */
export function snapBounds(bounds: GridBounds, resolution: number): GridBounds {
  const latStep = STEP_LATITUDE * resolution
  const lngStep = STEP_LONGITUDE * resolution

  let minLat = snapDown(bounds.minLat, ORIGIN_LATITUDE, latStep)
  let maxLat = snapUp(bounds.maxLat, ORIGIN_LATITUDE, latStep)
  let minLng = snapDown(bounds.minLng, ORIGIN_LONGITUDE, lngStep)
  let maxLng = snapUp(bounds.maxLng, ORIGIN_LONGITUDE, lngStep)

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
