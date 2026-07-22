import apiClient from '../../../api/client'
import type { AreaLocation, AreaMapData, AreaStatus, RecentReport } from './types'

export async function resolveLocation(latitude: number, longitude: number) {
  const { data } = await apiClient.get<AreaLocation>('/api/locations/resolve', { params: { latitude, longitude } })
  return data
}
export async function getAreaMap(areaId: number) { return (await apiClient.get<AreaMapData>(`/api/areas/${areaId}/map`)).data }
export async function getAreaStatus(areaId: number) { return (await apiClient.get<AreaStatus>(`/api/areas/${areaId}/status`)).data }
export async function getRecentReports(areaId: number) { return (await apiClient.get<RecentReport[]>('/api/reports/recent', { params: { areaId, limit: 3 } })).data }
