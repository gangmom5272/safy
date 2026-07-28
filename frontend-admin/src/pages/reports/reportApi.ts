import apiClient from '../../api/client'
import { mockResponse, USE_MOCK_API } from '../../api/mockMode'
import { mockDashboardReports, mockReportAnalysis, mockReportDetail } from '../../mocks/adminApiMocks'
import type { ApiResponse, PageResponse } from '../../api/types'
import type { AdminReport, LiveStreamStatus, ReportStatus, ReportType } from '../../types/admin'

type BackendReportStatus =
  | 'RECEIVED' | 'ASSIGNED' | 'ACCEPTED' | 'IN_PROGRESS' | 'CLOSED' | 'REJECTED' | 'CANCELLED'

type BackendCategory =
  | 'CROWD_CONGESTION' | 'ACCIDENT_RISK' | 'FLOOD_WEATHER' | 'FACILITY_ROAD' | 'OTHER'

interface DashboardImageView {
  imageCount: number
  status: string
  thumbnailUrl: string | null
  thumbnailUrlExpiresAt: string | null
}

interface DashboardLiveView {
  streamRoomId: string
  status: LiveStreamStatus
  startedAt: string | null
  expiresAt: string | null
}

export interface OperatorDashboardTile {
  assignmentId: number
  reportId: number
  reportType: 'IMAGE' | 'LIVE'
  category: BackendCategory
  subcategory: string
  description: string
  latitude: number | null
  longitude: number | null
  address: string | null
  reportStatus: BackendReportStatus
  assignmentStatus: string
  controlCenterId: number | null
  controlCenterName: string | null
  operatorId: number | null
  operatorName: string | null
  assignedAt: string
  image: DashboardImageView | null
  live: DashboardLiveView | null
}

export interface ReportAnalysis {
  reportId: number
  status: string
  summary: string | null
  riskLevel: string | null
  crowdCount: number | null
  confidence: number | null
  analyzedAt: string | null
}

const categoryToType: Record<BackendCategory, ReportType> = {
  CROWD_CONGESTION: 'crowd',
  ACCIDENT_RISK: 'accident',
  FLOOD_WEATHER: 'flood_weather',
  FACILITY_ROAD: 'facility_road',
  OTHER: 'etc',
}

// 백엔드는 7개 상태를 쓰지만 관리자 화면은 4단계로 표시한다.
// 종결 계열(CLOSED/REJECTED/CANCELLED)은 모두 완료로 묶는다.
function toUiStatus(status: BackendReportStatus): ReportStatus {
  if (status === 'RECEIVED') return 'received'
  if (status === 'ASSIGNED' || status === 'ACCEPTED') return 'checking'
  if (status === 'IN_PROGRESS') return 'in_progress'
  return 'completed'
}

export function toAdminReport(tile: OperatorDashboardTile): AdminReport {
  return {
    id: tile.reportId,
    assignmentId: tile.assignmentId,
    location: tile.address ?? '위치 정보 없음',
    type: categoryToType[tile.category] ?? 'etc',
    // 백엔드 대시보드 타일에는 위험도 필드가 없다.
    riskLevel: null,
    riskScore: null,
    reportedAt: tile.assignedAt,
    status: toUiStatus(tile.reportStatus),
    reviewStatus: null,
    aiAnalysisStatus: null,
    managerName: tile.operatorName ?? '미배정',
    aiSummary: tile.description,
    riskTags: [],
    history: [],
    imageUrl: tile.image?.thumbnailUrl ?? undefined,
    imageCount: tile.image?.imageCount ?? 0,
    mediaStatus: tile.image?.status ?? null,
    isLive: tile.reportType === 'LIVE',
    streamRoomId: tile.live?.streamRoomId ?? null,
    liveStatus: tile.live?.status ?? null,
    controlCenterName: tile.controlCenterName,
  }
}

export interface ReportImage {
  reportImageId: number
  mediaAssetId: number
  displayOrder: number
  storageStatus: string
  previewUrl: string | null
}

export interface OperatorReportDetail {
  reportId: number
  reportType: 'IMAGE' | 'LIVE'
  category: BackendCategory
  subcategory: string
  description: string
  latitude: number | null
  longitude: number | null
  address: string | null
  status: BackendReportStatus
  routingStatus: string | null
  administrativeAreaName: string | null
  assignedControlCenter: { name: string; administrativeAreaName: string | null } | null
  reportedAt: string
  images: ReportImage[]
}

// 담당자가 상세를 조회하면 백엔드가 제보를 '처리중'으로 자동 전이시킨다.
export async function getOperatorReportDetail(reportId: number) {
  if (USE_MOCK_API) return mockResponse(mockReportDetail(reportId))

  const response = await apiClient.get<ApiResponse<OperatorReportDetail>>(`/operator/reports/${reportId}`)
  return response.data.data
}

export function detailToAdminReport(detail: OperatorReportDetail): AdminReport {
  return {
    id: detail.reportId,
    location: detail.address ?? detail.administrativeAreaName ?? '위치 정보 없음',
    type: categoryToType[detail.category] ?? 'etc',
    riskLevel: null,
    riskScore: null,
    reportedAt: detail.reportedAt,
    status: toUiStatus(detail.status),
    reviewStatus: null,
    aiAnalysisStatus: null,
    managerName: detail.assignedControlCenter?.name ?? '미배정',
    aiSummary: detail.description,
    riskTags: [],
    history: [],
    imageUrl: detail.images.find((image) => image.previewUrl)?.previewUrl ?? undefined,
    isLive: detail.reportType === 'LIVE',
    controlCenterName: detail.assignedControlCenter?.name ?? null,
  }
}

export async function getDashboardReports(page = 0, size = 50) {
  if (USE_MOCK_API) return mockResponse(mockDashboardReports(size))

  const response = await apiClient.get<ApiResponse<PageResponse<OperatorDashboardTile>>>(
    '/operator/dashboard/reports',
    { params: { page, size } },
  )
  return response.data.data.content.map(toAdminReport)
}

export async function getReportAnalysis(reportId: number) {
  if (USE_MOCK_API) return mockResponse(mockReportAnalysis(reportId))

  const response = await apiClient.get<ApiResponse<ReportAnalysis>>(`/reports/${reportId}/analysis`)
  return response.data.data
}

export async function completeReport(reportId: number) {
  if (USE_MOCK_API) return mockResponse(undefined)

  await apiClient.patch(`/operator/reports/${reportId}/complete`)
}

export async function rejectReport(reportId: number, reason: string) {
  if (USE_MOCK_API) return mockResponse(undefined)

  await apiClient.patch(`/operator/reports/${reportId}/reject`, { reason })
}

export async function reassignReport(reportId: number, reason: string) {
  if (USE_MOCK_API) return mockResponse(undefined)

  await apiClient.patch(`/operator/reports/${reportId}/reassign`, { reason })
}
