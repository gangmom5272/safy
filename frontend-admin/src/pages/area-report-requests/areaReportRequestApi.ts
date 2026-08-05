import apiClient from '../../api/client'
import type { ApiResponse } from '../../api/types'

export interface RequestPlace {
  id: number
  name: string
  latitude: number
  longitude: number
}

export interface AreaReportRequestResult {
  id: number
  placeId: number
  placeName: string
  title: string
  content: string
  expiresAt: string
  recipientCount: number
}

export async function getRequestPlaces() {
  return (await apiClient.get<ApiResponse<RequestPlace[]>>('/admin/area-report-requests/places')).data.data
}

export async function createAreaReportRequest(input: {
  placeId: number
  title: string
  content: string
  expiresInMinutes: number
}) {
  return (await apiClient.post<ApiResponse<AreaReportRequestResult>>(
    '/admin/area-report-requests',
    input,
  )).data.data
}
