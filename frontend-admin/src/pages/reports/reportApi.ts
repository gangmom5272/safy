import apiClient from '../../api/client'
import { mockResponse, USE_MOCK_API } from '../../api/mockMode'
import {
  mockDashboardReports,
  mockReportAnalysis,
  mockReportDetail,
  mockTransferTargets,
} from '../../mocks/adminApiMocks'
import type { ApiResponse, PageResponse } from '../../api/types'
import type { AdminReport, LiveStreamStatus, ReportStatus, ReportType } from '../../types/admin'
import { resolveRiskLevel } from '../../utils/adminRisk'
import { verificationFromAssignmentStatus } from '../../utils/adminVerification'

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

/** 백엔드 RiskSummaryView. RiskLevel 은 소문자로 직렬화된다(very_high 등). */
interface ReportRiskView {
  status: string
  score: number | null
  level: string | null
  confidence: number
  confirmRequired: boolean
  assessedAt: string | null
}

/** 알 수 없는 등급 문자열이 배지에 그대로 흘러가지 않도록 5단계로 정규화한다. */
function toRiskLevel(risk: ReportRiskView | null) {
  return resolveRiskLevel(risk?.level, risk?.score)
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
  assignmentStatus: AssignmentStatus
  controlCenterId: number | null
  controlCenterName: string | null
  operatorId: number | null
  operatorName: string | null
  /** 시민이 제보한 시각. 정렬·표시 기준이다. */
  reportedAt: string
  /** 이 담당자에게 배정된 시각. 재배정되면 뒤로 밀린다. */
  assignedAt: string
  image: DashboardImageView | null
  live: DashboardLiveView | null
  risk: ReportRiskView | null
}

export interface ReportAnalysis {
  reportId: number
  status: string
  summary: string | null
  /** 백엔드 RiskLevel 은 소문자(very_high 등)로 직렬화된다. */
  riskLevel: string | null
  riskScore: number | null
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

function toUiStatus(status: BackendReportStatus): ReportStatus {
  if (status === 'RECEIVED') return 'received'
  if (status === 'ASSIGNED' || status === 'ACCEPTED') return 'checking'
  if (status === 'IN_PROGRESS') return 'in_progress'
  if (status === 'REJECTED') return 'rejected'
  if (status === 'CANCELLED') return 'cancelled'
  return 'completed'
}

export function toAdminReport(tile: OperatorDashboardTile): AdminReport {
  return {
    id: tile.reportId,
    assignmentId: tile.assignmentId,
    location: tile.address ?? '위치 정보 없음',
    latitude: tile.latitude,
    longitude: tile.longitude,
    type: categoryToType[tile.category] ?? 'etc',
    subcategory: tile.subcategory,
    riskLevel: toRiskLevel(tile.risk),
    riskScore: tile.risk?.score ?? null,
    // 배정 시각이 아니라 제보 시각을 쓴다. 재배정되면 배정 시각만 뒤로 밀려
    // '최신순' 정렬에서 제보 순서가 뒤섞인다.
    reportedAt: tile.reportedAt,
    assignedAt: tile.assignedAt,
    status: toUiStatus(tile.reportStatus),
    verificationStatus: verificationFromAssignmentStatus(tile.assignmentStatus),
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
  // 조회한 담당자 본인의 최신 배정 상태. SUPER_ADMIN 은 null.
  myAssignmentStatus: AssignmentStatus | null
}

// 담당자가 상세를 조회하면 백엔드가 제보를 '처리중'으로 자동 전이시킨다.
export async function getOperatorReportDetail(reportId: number) {
  if (USE_MOCK_API) return mockResponse(mockReportDetail(reportId))

  const response = await apiClient.get<ApiResponse<OperatorReportDetail>>(`/operator/reports/${reportId}`)
  return response.data.data
}

/** 상세에서도 목록과 같은 기준(내 배정 결과)으로 표시하기 위한 판정. */
export function toMyOutcome(detail: OperatorReportDetail): ReportStatus | null {
  if (!detail.myAssignmentStatus) return null
  if (detail.myAssignmentStatus === 'COMPLETED') return 'completed'
  if (detail.myAssignmentStatus === 'CANCELLED') {
    if (detail.status === 'REJECTED') return 'rejected'
    if (detail.status === 'CANCELLED') return 'cancelled'
    return 'reassigned'
  }
  return null
}

export function detailToAdminReport(detail: OperatorReportDetail): AdminReport {
  return {
    id: detail.reportId,
    location: detail.address ?? detail.administrativeAreaName ?? '위치 정보 없음',
    latitude: detail.latitude,
    longitude: detail.longitude,
    type: categoryToType[detail.category] ?? 'etc',
    subcategory: detail.subcategory,
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

export type AssignmentStatus = 'ASSIGNED' | 'ACCEPTED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'

export interface OperatorAssignment {
  assignmentId: number
  reportId: number
  assignmentStatus: AssignmentStatus
  reportStatus: BackendReportStatus
  reportType: 'IMAGE' | 'LIVE'
  category: BackendCategory
  subcategory: string
  description: string
  address: string | null
  administrativeAreaName: string | null
  controlCenterId: number | null
  controlCenterName: string | null
  operatorId: number | null
  operatorName: string | null
  /** 시민이 제보한 시각. 정렬·표시 기준이다. */
  reportedAt: string
  /** 이 담당자에게 배정된 시각. 재배정되면 뒤로 밀린다. */
  assignedAt: string
  acceptedAt: string | null
  startedAt: string | null
  completedAt: string | null
  risk: ReportRiskView | null
}

/**
 * 담당 이력 목록은 "제보가 지금 어떤 상태인가"가 아니라 "내 배정이 어떻게 끝났는가"를 보여줘야 한다.
 * 재배정하면 내 배정만 CANCELLED 되고 제보는 다른 담당자에게 넘어가 계속 진행되므로,
 * 제보 상태(ASSIGNED)를 그대로 쓰면 "확인 중"으로 보여 내가 넘겼다는 사실이 사라진다.
 */
function toAssignmentOutcome(assignment: OperatorAssignment): ReportStatus {
  if (assignment.assignmentStatus === 'COMPLETED') return 'completed'
  if (assignment.assignmentStatus === 'CANCELLED') {
    if (assignment.reportStatus === 'REJECTED') return 'rejected'
    if (assignment.reportStatus === 'CANCELLED') return 'cancelled'
    // 제보가 아직 살아 있는데 내 배정만 취소됐다면 재배정으로 넘긴 것이다.
    return 'reassigned'
  }
  return toUiStatus(assignment.reportStatus)
}

export function assignmentToAdminReport(assignment: OperatorAssignment): AdminReport {
  return {
    id: assignment.reportId,
    assignmentId: assignment.assignmentId,
    location: assignment.address ?? assignment.administrativeAreaName ?? '위치 정보 없음',
    type: categoryToType[assignment.category] ?? 'etc',
    subcategory: assignment.subcategory,
    riskLevel: toRiskLevel(assignment.risk),
    riskScore: assignment.risk?.score ?? null,
    // 배정 시각이 아니라 제보 시각을 쓴다. 재배정되면 배정 시각만 뒤로 밀려
    // '최신순' 정렬에서 제보 순서가 뒤섞인다.
    reportedAt: assignment.reportedAt,
    assignedAt: assignment.assignedAt,
    status: toAssignmentOutcome(assignment),
    verificationStatus: verificationFromAssignmentStatus(assignment.assignmentStatus),
    reviewStatus: null,
    aiAnalysisStatus: null,
    managerName: assignment.operatorName ?? '미배정',
    aiSummary: assignment.description,
    riskTags: [],
    history: [],
    isLive: assignment.reportType === 'LIVE',
    controlCenterName: assignment.controlCenterName,
    assignmentStatus: assignment.assignmentStatus,
    completedAt: assignment.completedAt,
  }
}

/**
 * 내가 담당한 제보 이력. status 를 지정하지 않으면 종결 건까지 전부 조회한다.
 * 관제 모니터링(진행 중 작업 큐)과 달리 지난 담당 내역을 보기 위한 목록이다.
 */
export async function getMyAssignedReports(page = 0, size = 100) {
  if (USE_MOCK_API) return mockResponse(mockDashboardReports(size))

  const response = await apiClient.get<ApiResponse<PageResponse<OperatorAssignment>>>(
    '/operator/reports/me',
    { params: { page, size, sort: 'assignedAt,desc' } },
  )
  return response.data.data.content.map(assignmentToAdminReport)
}

export async function getReportAnalysis(reportId: number) {
  if (USE_MOCK_API) return mockResponse(mockReportAnalysis(reportId))

  const response = await apiClient.get<ApiResponse<ReportAnalysis>>(`/reports/${reportId}/analysis`)
  return response.data.data
}

/**
 * 제보를 '관리자 확인'으로 표시하는 연동 지점.
 *
 * 백엔드에 전용 확인 API 는 없다. 담당자가 상세를 조회하면(GET /operator/reports/{reportId})
 * 서버가 ASSIGNED → IN_PROGRESS 로 전이시키고 확인 시각(assignment.startedAt)과
 * 'OPERATOR_VIEWING' 이력을 남기므로, 상세 이동 자체가 확인 처리를 겸한다.
 * 지금은 네트워크 호출 없이 화면 상태만 맞추고, 전용 API 가 생기면 이 함수만 교체한다.
 */
export function markReportChecked(reportId: number): Promise<void> {
  void reportId
  return Promise.resolve()
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

export interface TransferTarget {
  controlCenterId: number
  controlCenterName: string
  coverageRole: 'PRIMARY' | 'BACKUP'
  priority: number
  hasOnDutyOperator: boolean
  /** 이 제보가 이미 거쳐온 센터. */
  visited: boolean
  /** 선택 가능 여부. 판정 규칙은 서버가 갖고 있으므로 여기서 다시 계산하지 않는다. */
  transferable: boolean
}

/** 담당자는 관제센터 목록 API 권한이 없어서 이관 선택지는 이 API 로만 채운다. */
export async function getTransferTargets(reportId: number) {
  if (USE_MOCK_API) return mockResponse(mockTransferTargets())

  const response = await apiClient.get<ApiResponse<TransferTarget[]>>(
    `/operator/reports/${reportId}/transfer-targets`,
  )
  return response.data.data
}

export async function transferReport(reportId: number, targetControlCenterId: number, reason: string) {
  if (USE_MOCK_API) return mockResponse(undefined)

  await apiClient.post(`/operator/reports/${reportId}/transfer`, { targetControlCenterId, reason })
}
