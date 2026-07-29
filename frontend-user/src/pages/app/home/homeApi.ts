import apiClient from '../../../api/client'
import { mockResponse, USE_MOCK_API } from '../../../api/mockMode'
import type { ApiResponse } from '../../../api/types'
import type { AreaLocation, AreaMapData, AreaStatus, RecentReport } from './types'

export async function resolveLocation(latitude: number, longitude: number) {
  if (USE_MOCK_API) {
    return mockResponse<AreaLocation>({ resolved: true, areaId: 1, areaName: '마포구', address: '서울 마포구 서교동 358', latitude, longitude })
  }

  const { data } = await apiClient.get<ApiResponse<AreaLocation>>('/locations/resolve', { params: { latitude, longitude } })
  return data.data
}
export async function getAreaMap(areaId: number) { return (await apiClient.get<AreaMapData>(`/api/areas/${areaId}/map`)).data }
export async function getAreaStatus(areaId: number) { return (await apiClient.get<AreaStatus>(`/api/areas/${areaId}/status`)).data }
export async function getRecentReports(areaId: number) { return (await apiClient.get<RecentReport[]>('/api/reports/recent', { params: { areaId, limit: 3 } })).data }
