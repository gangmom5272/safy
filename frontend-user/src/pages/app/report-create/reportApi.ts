import apiClient from '../../../api/client'
import { mockResponse, USE_MOCK_API } from '../../../api/mockMode'
import type { ApiResponse } from '../../../api/types'

export type ReportTypeValue = 'IMAGE' | 'LIVE'
export type ReportCategoryValue = 'CROWD_CONGESTION' | 'ACCIDENT_RISK' | 'FLOOD_WEATHER' | 'FACILITY_ROAD' | 'OTHER'
export type ReportSubcategory =
  | 'MOVEMENT_DIFFICULTY' | 'EXIT_CONGESTION' | 'QUEUE_INCREASE' | 'CROWD_INCREASING' | 'CRUSH_RISK'
  | 'TRAFFIC_ACCIDENT' | 'FIRE_SMOKE' | 'PERSON_INJURED' | 'FALL_COLLISION_RISK' | 'VIOLENCE_THREAT'
  | 'ROAD_FLOODING' | 'BUILDING_FLOODING' | 'HEAVY_RAIN' | 'HEAVY_SNOW_ICE' | 'STRONG_WIND'
  | 'ROAD_DAMAGE' | 'TRAFFIC_SIGNAL_FAILURE' | 'STREETLIGHT_FAILURE' | 'PUBLIC_FACILITY_DAMAGE'
  | 'ROAD_OBSTRUCTION' | 'OTHER_SITUATION'

export interface CreateReportRequest {
  reportType: ReportTypeValue
  category: ReportCategoryValue
  subcategory: ReportSubcategory
  description: string
  latitude: number
  longitude: number
}

export interface CreateReportResponse {
  reportId: number
  reportType: ReportTypeValue
  category: ReportCategoryValue
  subcategory: ReportSubcategory
  status: 'RECEIVED'
  routingStatus: 'PENDING'
  reportedAt: string
}

export async function createReport(request: CreateReportRequest) {
  if (USE_MOCK_API) {
    return mockResponse<CreateReportResponse>({
      reportId: 9001,
      reportType: request.reportType,
      category: request.category,
      subcategory: request.subcategory,
      status: 'RECEIVED',
      routingStatus: 'PENDING',
      reportedAt: new Date().toISOString(),
    })
  }

  const response = await apiClient.post<ApiResponse<CreateReportResponse>>('/reports', request)
  return response.data.data
}
