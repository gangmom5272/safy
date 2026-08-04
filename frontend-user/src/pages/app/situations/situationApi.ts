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
import { findDistrictCenter } from './seoulDistrictCenters'
import {
  createLiveReportParams,
  mapAreaDetail,
  mapAreaMapData,
  mapAreaStatus,
  mapLiveReport,
  mapLocationSearchResult,
  mapReportAnalysis,
  mapReportDetail,
  mapReportWebRtcInfo,
  readArrayResponse,
  unwrapEnvelope,
} from './situationMapper'
import type {
  AreaDetail,
  AreaMapData,
  AreaStatus,
  LiveReportSummary,
  LocationSearchResult,
  ReportAnalysis,
  ReportDetail,
  ReportListParams,
  ReportWebRtcInfo,
} from './types'

function waitForMock<T>(data: T): Promise<T> {
  return new Promise((resolve) => {
    window.setTimeout(() => resolve(data), 180)
  })
}

export async function searchLocations(keyword: string): Promise<LocationSearchResult[]> {
  if (USE_MOCK_API) {
    return waitForMock(filterMockLocations(keyword))
  }

  // 백엔드가 요구하는 파라미터명은 query 다. keyword 로 보내면 필수값 누락으로 400이 온다.
  const response = await apiClient.get<unknown>('/locations/search', {
    params: { query: keyword },
  })

  // 검색 응답에는 좌표가 없어 지도를 옮길 수 없다. 자치구 중심 좌표로 보강한다.
  return readArrayResponse(response.data)
    .map(mapLocationSearchResult)
    .map((location) => {
      if (location.latitude !== undefined && location.longitude !== undefined) {
        return location
      }
      const center = findDistrictCenter(location.name)
      return center
        ? { ...location, latitude: center.latitude, longitude: center.longitude }
        : location
    })
}

export async function getLiveReports(params: ReportListParams): Promise<LiveReportSummary[]> {
  if (USE_MOCK_API) {
    return waitForMock(filterAndSortMockReports(params.types, params.sort).slice(0, params.limit))
  }

  // 공개 피드(/feed)는 담당 관제사가 정상 제보로 판정한 건만 내려준다.
  // 관제용 /reports/live 는 라이브 세션 정보를 담고 있어 시민에게 열려 있지 않다.
  const response = await apiClient.get<unknown>('/feed/reports', {
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

  const response = await apiClient.get<unknown>(`/feed/areas/${areaIdOrQuery}/reports`, {
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

  // /reports/{id} 는 제보자 본인만 볼 수 있어 남의 제보 카드에서는 404 였다. 공개 피드 상세로 조회한다.
  const response = await apiClient.get<unknown>(`/feed/reports/${reportId}`)

  return mapReportDetail(unwrapEnvelope(response.data))
}

export async function getReportAnalysis(reportId: string): Promise<ReportAnalysis | null> {
  if (USE_MOCK_API) {
    return waitForMock(getMockReportAnalysis(reportId) ?? null)
  }

  const response = await apiClient.get<unknown | null>(`/feed/reports/${reportId}/analysis`)
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
