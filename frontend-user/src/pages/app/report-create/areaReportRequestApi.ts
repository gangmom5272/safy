import apiClient from '../../../api/client'
import type { ApiResponse } from '../../../api/types'

export interface AreaReportRequest {
  id: number
  placeId: number
  placeName: string
  title: string
  content: string
  latitude: number
  longitude: number
  requestedAt: string
  expiresAt: string
  recipientCount: number
}

export async function getAreaReportRequest(requestId: string) {
  return (await apiClient.get<ApiResponse<AreaReportRequest>>(
    `/area-report-requests/${encodeURIComponent(requestId)}`,
  )).data.data
}
