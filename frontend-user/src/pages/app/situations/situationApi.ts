import apiClient from '../../../api/client'
import { USE_MOCK_API } from '../../../api/mockMode'
import {
  filterAndSortMockAreaReports,
  filterAndSortMockReports,
  filterMockLocations,
  getMockArea,
  getMockAreaByQuery,
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

function toTypeParam(types: ReportType[]) {
  return types.length ? types.join(',') : undefined
}

function waitForMock<T>(data: T): Promise<T> {
  return new Promise((resolve) => {
    window.setTimeout(() => resolve(data), 180)
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

// 백엔드는 모든 응답을 { success, data } 로 감싼다.
function unwrapEnvelope(raw: unknown): unknown {
  const item = asRecord(raw)

  return 'success' in item && 'data' in item ? item.data : raw
}

function readArrayResponse(raw: unknown): unknown[] {
  const unwrapped = unwrapEnvelope(raw)

  if (Array.isArray(unwrapped)) {
    return unwrapped
  }

  const item = asRecord(unwrapped)

  // 커서 페이지 응답은 content 에 목록이 담긴다.
  if (Array.isArray(item.content)) {
    return item.content
  }

  if (Array.isArray(item.items)) {
    return item.items
  }

  if (Array.isArray(item.data)) {
    return item.data
  }

  return []
}

function normalizeReportType(value: unknown): ReportType {
  if (value === 'crowd' || value === 'CROWD_CONGESTION' || value === '인파 밀집') {
    return 'crowd'
  }

  if (value === 'accident' || value === 'ACCIDENT_RISK' || value === '사고·위험') {
    return 'accident'
  }

  if (value === 'flood_weather' || value === 'FLOOD_WEATHER' || value === 'weather' || value === '침수·기상') {
    return 'flood_weather'
  }

  if (value === 'facility_road' || value === 'FACILITY_ROAD' || value === 'facility' || value === '시설물·도로') {
    return 'facility_road'
  }

  return 'etc'
}

// 라이브 세션이 열려 있으면(CREATED/ACTIVE) 송출 중으로 본다.
function isLiveStreamStatus(value: unknown) {
  return value === 'CREATED' || value === 'ACTIVE'
}

export function normalizeRiskLevel(value: unknown): RiskLevel {
  if (value === 'critical' || value === 'very_high' || value === '매우 높음' || value === 4) {
    return 'critical'
  }

  if (value === 'high' || value === 'HIGH' || value === '높음' || value === 3) {
    return 'high'
  }

  if (value === 'medium' || value === 'MEDIUM' || value === 'normal' || value === '보통' || value === 2) {
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

  if (value === 'smooth' || value === '여유') {
    return 'smooth'
  }

  return 'normal'
}

function normalizeAnalysisStatus(value: unknown): AnalysisStatus {
  if (value === 'processing' || value === 'completed' || value === 'failed') {
    return value
  }

  if (value === 'PROCESSING') return 'processing'
  if (value === 'COMPLETED') return 'completed'
  if (value === 'FAILED') return 'failed'

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

  // 백엔드 LiveSessionStatus 를 화면 상태로 옮긴다.
  if (value === 'CREATED') return 'waiting'
  if (value === 'ACTIVE') return 'connected'
  if (value === 'DISCONNECTED') return 'connecting'
  if (value === 'ENDED') return 'ended'
  if (value === 'FAILED') return 'failed'

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
    // 지역명 검색 응답은 주소를 displayName 으로 준다.
    address: asString(item.address ?? item.roadAddress ?? item.displayName),
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

  const description = asString(item.description ?? item.summary)
  const isLive = item.streamStatus === undefined
    ? asBoolean(item.isLive ?? item.live)
    : isLiveStreamStatus(item.streamStatus)

  return {
    id: asString(item.id ?? item.reportId),
    // 백엔드 피드에는 제목 필드가 없어 본문 앞부분을 제목으로 쓴다.
    title: asString(item.title, '') || description || '제목 없는 제보',
    type: normalizeReportType(item.type ?? item.category),
    locationName: asString(item.locationName ?? item.areaName ?? item.placeName),
    address: asString(item.address),
    latitude: asOptionalNumber(item.latitude ?? item.lat),
    longitude: asOptionalNumber(item.longitude ?? item.lng),
    reportedAt: asString(item.reportedAt ?? item.createdAt ?? item.createdDate),
    relativeTime: asString(item.relativeTime),
    riskLevel: normalizeRiskLevel(item.riskLevel ?? item.risk ?? item.riskScore),
    description,
    maskedThumbnailUrl:
      asString(item.maskedThumbnailUrl ?? item.thumbnailUrl ?? item.maskedImageUrl, '') || null,
    isWebRtc: item.streamRoomId ? true : asBoolean(item.isWebRtc ?? item.webRtc ?? item.webrtc),
    isLive,
    viewerCount: asOptionalNumber(item.viewerCount ?? item.participantCount),
    thumbnailType: asString(item.thumbnailType),
  }
}

export function mapAreaMapData(raw: unknown): AreaMapData {
  const item = asRecord(raw)
  const center = asRecord(item.center)
  const zones = Array.isArray(item.zones) ? item.zones : []
  // 백엔드 AreaMapResponse 는 마커를 reports.content 로 내려준다.
  const markerSource = item.reportMarkers ?? item.markers ?? asRecord(item.reports).content
  const markers = Array.isArray(markerSource) ? markerSource : []
  const firstMarker = asRecord(markers[0])

  return {
    center: {
      // 백엔드에 중심 좌표 필드가 없어 첫 제보 위치로 대체한다.
      latitude: asNumber(center.latitude ?? center.lat ?? firstMarker.latitude, 0),
      longitude: asNumber(center.longitude ?? center.lng ?? firstMarker.longitude, 0),
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
    recentReportCount: asNumber(item.recentReportCount ?? item.activeReportCount ?? item.reportCount, 0),
    weather: item.weather
      ? {
          condition: asString(weather.condition),
          temperature: asOptionalNumber(weather.temperature),
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
    congestionLevel: item.congestionLevel ?? item.riskLevel
      ? normalizeRiskLevel(item.congestionLevel ?? item.riskLevel)
      : undefined,
    estimatedPeopleCount: asOptionalNumber(item.estimatedPeopleCount ?? item.crowdCount),
    aiSummary: asString(item.aiSummary ?? item.summary),
    analyzedAt: asString(item.analyzedAt),
    errorMessage: asString(item.errorMessage),
  }
}

export function mapReportWebRtcInfo(raw: unknown): ReportWebRtcInfo {
  const item = asRecord(raw)

  return {
    reportId: asString(item.reportId),
    roomId: asString(item.roomId ?? item.streamRoomId),
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

  // 백엔드가 요구하는 파라미터명은 query 다. keyword 로 보내면 필수값 누락으로 400이 온다.
  const response = await apiClient.get<unknown>('/locations/search', {
    params: { query: keyword },
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

  const response = await apiClient.get<unknown>(`/administrative-areas/${areaId}`)

  return mapAreaDetail(unwrapEnvelope(response.data))
}

export async function getAreaByQuery(keyword: string): Promise<AreaDetail | null> {
  if (USE_MOCK_API) {
    return waitForMock(getMockAreaByQuery(keyword))
  }

  const locations = await searchLocations(keyword)
  const location = locations[0]

  return location
    ? {
        id: location.id,
        name: location.name,
        address: location.address,
        latitude: location.latitude ?? 0,
        longitude: location.longitude ?? 0,
      }
    : null
}

export async function getAreaMap(areaIdOrQuery: string): Promise<AreaMapData> {
  if (USE_MOCK_API) {
    return waitForMock(getMockAreaMap(areaIdOrQuery))
  }

  const response = await apiClient.get<unknown>(`/areas/${areaIdOrQuery}/map`)

  return mapAreaMapData(unwrapEnvelope(response.data))
}

export async function getAreaStatus(areaId: string): Promise<AreaStatus> {
  if (USE_MOCK_API) {
    return waitForMock(mockAreaStatus)
  }

  const response = await apiClient.get<unknown>(`/areas/${areaId}/status`)

  return mapAreaStatus(unwrapEnvelope(response.data))
}

export async function getAreaLiveReports(
  areaIdOrQuery: string,
  params: ReportListParams,
): Promise<LiveReportSummary[]> {
  if (USE_MOCK_API) {
    return waitForMock(filterAndSortMockAreaReports(areaIdOrQuery, params.types, params.sort))
  }

  const response = await apiClient.get<unknown>(`/areas/${areaIdOrQuery}/reports/live`, {
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

  return mapReportDetail(unwrapEnvelope(response.data))
}

export async function getReportAnalysis(reportId: string): Promise<ReportAnalysis | null> {
  if (USE_MOCK_API) {
    return waitForMock(getMockReportAnalysis(reportId) ?? null)
  }

  const response = await apiClient.get<unknown | null>(`/reports/${reportId}/analysis`)
  const analysis = unwrapEnvelope(response.data)

  return analysis ? mapReportAnalysis(analysis) : null
}

export async function getReportWebRtcInfo(reportId: string): Promise<ReportWebRtcInfo> {
  if (USE_MOCK_API) {
    return waitForMock(getMockWebRtcInfo(reportId))
  }

  const response = await apiClient.get<unknown>(`/reports/${reportId}/webrtc`)

  return mapReportWebRtcInfo(unwrapEnvelope(response.data))
}
