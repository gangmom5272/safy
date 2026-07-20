import apiClient from '../../../api/client'
import {
  filterAndSortMockAreaReports,
  filterAndSortMockReports,
  filterMockLocations,
  getMockArea,
  getMockAreaMap,
  getMockReportAnalysis,
  getMockReportDetail,
  getMockWebRtcInfo,
  mockAreaStatus,
} from './mockData'
import type {
  AnalysisStatus,
  AreaDetail,
  AreaMapData,
  AreaStatus,
  CongestionLevel,
  LiveReportSummary,
  LocationSearchResult,
  ReportAnalysis,
  ReportDetail,
  ReportListParams,
  ReportType,
  ReportWebRtcInfo,
  RiskLevel,
  WebRtcStatus,
} from './types'
import { DEFAULT_REPORT_LIMIT } from './types'

const USE_MOCK_API = import.meta.env.VITE_USE_MOCK_API === 'true'

function toTypeParam(types: ReportType[]) {
  return types.length ? types.join(',') : undefined
}

function waitForMock<T>(data: T): Promise<T> {
  return new Promise((resolve) => {
    window.setTimeout(() => resolve(data), 250)
  })
}

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' ? (value as Record<string, unknown>) : {}
}

function asString(value: unknown, fallback = '') {
  return typeof value === 'string' ? value : fallback
}

function asNumber(value: unknown, fallback = 0) {
  return typeof value === 'number' && Number.isFinite(value) ? value : fallback
}

function asOptionalNumber(value: unknown) {
  return typeof value === 'number' && Number.isFinite(value) ? value : undefined
}

function asBoolean(value: unknown) {
  return value === true
}

function readArrayResponse(raw: unknown): unknown[] {
  if (Array.isArray(raw)) {
    return raw
  }

  const item = asRecord(raw)

  if (Array.isArray(item.data)) {
    return item.data
  }

  if (Array.isArray(item.items)) {
    return item.items
  }

  if (Array.isArray(item.content)) {
    return item.content
  }

  return []
}

function normalizeReportType(value: unknown): ReportType {
  if (value === 'crowd' || value === '인파 밀집') {
    return 'crowd'
  }

  if (value === 'accident' || value === '사고·위험' || value === '사고위험') {
    return 'accident'
  }

  if (value === 'flood_weather' || value === '침수·기상' || value === '침수기상') {
    return 'flood_weather'
  }

  if (value === 'facility_road' || value === '시설물·도로' || value === '시설물도로') {
    return 'facility_road'
  }

  return 'etc'
}

export function normalizeRiskLevel(value: unknown): RiskLevel {
  if (value === 'critical' || value === 'very_high' || value === '매우 높음' || value === 4) {
    return 'critical'
  }

  if (value === 'high' || value === '높음' || value === 3) {
    return 'high'
  }

  if (value === 'medium' || value === 'normal' || value === '보통' || value === 2) {
    return 'medium'
  }

  return 'low'
}

function normalizeCongestionLevel(value: unknown): CongestionLevel {
  if (value === 'very_crowded' || value === '매우 혼잡') {
    return 'very_crowded'
  }

  if (value === 'crowded' || value === '혼잡') {
    return 'crowded'
  }

  if (value === 'smooth' || value === '원활') {
    return 'smooth'
  }

  return 'normal'
}

function normalizeAnalysisStatus(value: unknown): AnalysisStatus {
  if (value === 'processing' || value === 'completed' || value === 'failed') {
    return value
  }

  return 'pending'
}

function normalizeWebRtcStatus(value: unknown): WebRtcStatus {
  if (
    value === 'waiting' ||
    value === 'connecting' ||
    value === 'connected' ||
    value === 'ended' ||
    value === 'failed'
  ) {
    return value
  }

  return 'none'
}

export function createLiveReportParams({ sort, types, limit }: ReportListParams) {
  const params: Record<string, string | number> = {
    sort,
    limit: limit ?? DEFAULT_REPORT_LIMIT,
  }
  const type = toTypeParam(types)

  if (type) {
    params.type = type
  }

  return params
}

export function mapLocationSearchResult(raw: unknown): LocationSearchResult {
  const item = asRecord(raw)

  return {
    id: asString(item.id ?? item.areaId ?? item.locationId),
    name: asString(item.name ?? item.areaName ?? item.locationName),
    address: asString(item.address ?? item.roadAddress),
    latitude: asOptionalNumber(item.latitude ?? item.lat),
    longitude: asOptionalNumber(item.longitude ?? item.lng),
  }
}

export function mapAreaDetail(raw: unknown): AreaDetail {
  const item = asRecord(raw)
  const location = mapLocationSearchResult(item)
  const center = asRecord(item.center)

  return {
    id: location.id,
    name: location.name,
    address: location.address,
    latitude: location.latitude ?? asNumber(item.centerLatitude ?? center.latitude, 0),
    longitude: location.longitude ?? asNumber(item.centerLongitude ?? center.longitude, 0),
  }
}

export function mapLiveReport(raw: unknown): LiveReportSummary {
  const item = asRecord(raw)

  return {
    id: asString(item.id ?? item.reportId),
    title: asString(item.title, '제목 없는 제보'),
    type: normalizeReportType(item.type ?? item.category),
    locationName: asString(item.locationName ?? item.placeName),
    address: asString(item.address),
    reportedAt: asString(item.reportedAt ?? item.createdAt ?? item.createdDate),
    riskLevel: normalizeRiskLevel(item.riskLevel ?? item.risk ?? item.riskScore),
    description: asString(item.description ?? item.summary),
    maskedThumbnailUrl: asString(
      item.maskedThumbnailUrl ?? item.thumbnailUrl ?? item.maskedImageUrl,
      '',
    ) || null,
    isWebRtc: asBoolean(item.isWebRtc ?? item.webRtc ?? item.webrtc),
    isLive: asBoolean(item.isLive ?? item.live),
  }
}

export function mapAreaMapData(raw: unknown): AreaMapData {
  const item = asRecord(raw)
  const center = asRecord(item.center)
  const zones = Array.isArray(item.zones) ? item.zones : []
  const markerSource = item.reportMarkers ?? item.markers
  const markers = Array.isArray(markerSource) ? markerSource : []

  return {
    center: {
      latitude: asNumber(center.latitude ?? center.lat, 0),
      longitude: asNumber(center.longitude ?? center.lng, 0),
    },
    zones: zones.map((zoneRaw) => {
      const zone = asRecord(zoneRaw)
      const coordinates = Array.isArray(zone.coordinates) ? zone.coordinates : []

      return {
        id: asString(zone.id),
        name: asString(zone.name ?? zone.label),
        level: normalizeRiskLevel(zone.level ?? zone.riskLevel),
        coordinates: coordinates.map((coordinateRaw) => {
          const coordinate = asRecord(coordinateRaw)

          return {
            latitude: asNumber(coordinate.latitude ?? coordinate.lat, 0),
            longitude: asNumber(coordinate.longitude ?? coordinate.lng, 0),
          }
        }),
      }
    }),
    reportMarkers: markers.map((markerRaw) => {
      const marker = asRecord(markerRaw)

      return {
        reportId: asString(marker.reportId ?? marker.id),
        latitude: asNumber(marker.latitude ?? marker.lat, 0),
        longitude: asNumber(marker.longitude ?? marker.lng, 0),
        locationName: asString(marker.locationName ?? marker.title),
        riskLevel: normalizeRiskLevel(marker.riskLevel ?? marker.risk),
      }
    }),
  }
}

export function mapAreaStatus(raw: unknown): AreaStatus {
  const item = asRecord(raw)
  const weather = asRecord(item.weather)

  return {
    riskLevel: normalizeRiskLevel(item.riskLevel ?? item.risk),
    congestionLevel: normalizeCongestionLevel(item.congestionLevel),
    recentReportCount: asNumber(item.recentReportCount ?? item.reportCount, 0),
    weather: item.weather
      ? {
          condition: asString(weather.condition),
          temperature:
            typeof weather.temperature === 'number' ? weather.temperature : undefined,
        }
      : undefined,
    updatedAt: asString(item.updatedAt ?? item.analyzedAt),
  }
}

export function mapReportDetail(raw: unknown): ReportDetail {
  const item = asRecord(raw)

  return {
    ...mapLiveReport(item),
    maskedMediaUrl: asString(item.maskedMediaUrl ?? item.maskedVideoUrl, '') || null,
  }
}

export function mapReportAnalysis(raw: unknown): ReportAnalysis {
  const item = asRecord(raw)

  return {
    reportId: asString(item.reportId),
    status: normalizeAnalysisStatus(item.status),
    congestionLevel: item.congestionLevel ? normalizeRiskLevel(item.congestionLevel) : undefined,
    estimatedPeopleCount:
      typeof item.estimatedPeopleCount === 'number' ? item.estimatedPeopleCount : undefined,
    aiSummary: asString(item.aiSummary ?? item.summary),
    analyzedAt: asString(item.analyzedAt),
    errorMessage: asString(item.errorMessage),
  }
}

export function mapReportWebRtcInfo(raw: unknown): ReportWebRtcInfo {
  const item = asRecord(raw)

  return {
    reportId: asString(item.reportId),
    roomId: asString(item.roomId),
    signalingUrl: asString(item.signalingUrl),
    statusSocketUrl: asString(item.statusSocketUrl),
    status: normalizeWebRtcStatus(item.status),
    iceServers: Array.isArray(item.iceServers) ? (item.iceServers as RTCIceServer[]) : undefined,
  }
}

export async function searchLocations(keyword: string): Promise<LocationSearchResult[]> {
  if (USE_MOCK_API) {
    return waitForMock(filterMockLocations(keyword))
  }

  const response = await apiClient.get<unknown>('/locations/search', {
    params: { keyword },
  })

  return readArrayResponse(response.data).map(mapLocationSearchResult)
}

export async function getLiveReports(params: ReportListParams): Promise<LiveReportSummary[]> {
  if (USE_MOCK_API) {
    return waitForMock(filterAndSortMockReports(params.types, params.sort).slice(0, params.limit))
  }

  const response = await apiClient.get<unknown>('/reports/live', {
    params: createLiveReportParams(params),
  })

  return readArrayResponse(response.data).map(mapLiveReport)
}

export async function getArea(areaId: string): Promise<AreaDetail> {
  if (USE_MOCK_API) {
    return waitForMock(getMockArea(areaId))
  }

  const response = await apiClient.get<unknown>(`/areas/${areaId}`)

  return mapAreaDetail(response.data)
}

export async function getAreaMap(areaId: string): Promise<AreaMapData> {
  if (USE_MOCK_API) {
    return waitForMock(getMockAreaMap(areaId))
  }

  const response = await apiClient.get<unknown>(`/areas/${areaId}/map`)

  return mapAreaMapData(response.data)
}

export async function getAreaStatus(areaId: string): Promise<AreaStatus> {
  if (USE_MOCK_API) {
    return waitForMock(mockAreaStatus)
  }

  const response = await apiClient.get<unknown>(`/areas/${areaId}/status`)

  return mapAreaStatus(response.data)
}

export async function getAreaLiveReports(
  areaId: string,
  params: ReportListParams,
): Promise<LiveReportSummary[]> {
  if (USE_MOCK_API) {
    return waitForMock(filterAndSortMockAreaReports(areaId, params.types, params.sort))
  }

  const response = await apiClient.get<unknown>(`/areas/${areaId}/reports/live`, {
    params: createLiveReportParams(params),
  })

  return readArrayResponse(response.data).map(mapLiveReport)
}

export async function getReportDetail(reportId: string): Promise<ReportDetail> {
  if (USE_MOCK_API) {
    const detail = getMockReportDetail(reportId)

    if (!detail) {
      throw new Error('해당 제보 정보를 찾을 수 없습니다.')
    }

    return waitForMock(detail)
  }

  const response = await apiClient.get<unknown>(`/reports/${reportId}`)

  return mapReportDetail(response.data)
}

export async function getReportAnalysis(reportId: string): Promise<ReportAnalysis | null> {
  if (USE_MOCK_API) {
    return waitForMock(getMockReportAnalysis(reportId) ?? null)
  }

  const response = await apiClient.get<unknown | null>(`/reports/${reportId}/analysis`)

  return response.data ? mapReportAnalysis(response.data) : null
}

export async function getReportWebRtcInfo(reportId: string): Promise<ReportWebRtcInfo> {
  if (USE_MOCK_API) {
    return waitForMock(getMockWebRtcInfo(reportId))
  }

  const response = await apiClient.get<unknown>(`/reports/${reportId}/webrtc`)

  return mapReportWebRtcInfo(response.data)
}
