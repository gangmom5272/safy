import apiClient from '../../api/client'
import { mockResponse, USE_MOCK_API } from '../../api/mockMode'
import {
  mockDashboardReports,
  mockReportAnalysis,
  mockReportDetail,
  mockReportHandlingHistory,
  mockTransferTargets,
} from '../../mocks/adminApiMocks'
import type { ApiResponse, PageResponse } from '../../api/types'
import type { AdminReport, LiveStreamStatus, ReportStatus, ReportType } from '../../types/admin'
import { resolveRiskLevel } from '../../utils/adminRisk'
import { verificationFromAssignmentStatus } from '../../utils/adminVerification'

type BackendReportStatus =
  | 'RECEIVED' | 'ASSIGNED' | 'ACCEPTED' | 'IN_PROGRESS' | 'CONFIRMED' | 'CLOSED' | 'REJECTED' | 'CANCELLED'

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
  if (status === 'CONFIRMED') return 'confirmed'
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

export type RecordingStatus = 'STARTING' | 'ACTIVE' | 'COMPLETED' | 'FAILED' | 'ABORTED'

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
  /** 현재 배정된 담당자. 아직 배정 전이거나 종결됐으면 null. */
  assignedOperatorId: number | null
  assignedOperatorName: string | null
  reportedAt: string
  images: ReportImage[]
  // 조회한 담당자 본인의 최신 배정 상태. SUPER_ADMIN 은 null.
  myAssignmentStatus: AssignmentStatus | null
  // LIVE 제보의 원본 녹화 상태. 라이브가 아니거나 녹화 시도가 없으면 null 이다.
  recordingStatus: RecordingStatus | null
  // 최신 COMPLETED 녹화의 presigned URL. 아직 준비되지 않았거나 실패했으면 null 이다.
  originalVideoUrl: string | null
}

/**
 * 담당 이력 타임라인의 사건 종류. 백엔드 ReportHandlingEventType 과 같은 값이다.
 * 제보 상태(ReportStatus)와 다른 축이라는 점이 중요하다. 재배정과 이관은 상태 전이가
 * 서로 완전히 같아 상태만으로는 구분되지 않으므로 반드시 이 값으로 판별한다.
 */
export type ReportHandlingEventType =
  | 'AUTO_ASSIGNED' | 'CLAIMED' | 'VIEWED' | 'CONFIRMED' | 'COMPLETED' | 'REJECTED'
  | 'REASSIGNED' | 'TRANSFERRED' | 'AUTO_TRANSFERRED'
  | 'ASSIGNMENT_TIMEOUT' | 'OPERATOR_OFFLINE' | 'OPERATOR_AWAY' | 'REPORTER_CANCELLED'

export interface ReportHandlingEvent {
  type: ReportHandlingEventType
  occurredAt: string
  /** 조치한 담당자. 자동 배정과 시스템 자동 이관은 null 이다. */
  operatorId: number | null
  operatorName: string | null
  /** 이 조치가 일어난 관제센터. 이관에서는 보내는 쪽이다. */
  controlCenterId: number | null
  controlCenterName: string | null
  /** 이관 목적지. TRANSFERRED·AUTO_TRANSFERRED 에서만 채워진다. */
  toControlCenterId: number | null
  toControlCenterName: string | null
  /** 담당자가 입력한 원본 사유. 자동 회수에는 사람이 적은 문장이 없어 null 이다. */
  reason: string | null
}

export interface ReportHandlingStages {
  receivedAt: string
  /** 담당자가 처음 상세를 연 시각. 재배정으로 왕복해도 '최초' 값이라 뒤로 밀리지 않는다. */
  firstInProgressAt: string | null
  closedAt: string | null
  currentStatus: BackendReportStatus
  reassignCount: number
  transferCount: number
}

export interface ReportHandlingHistory {
  reportId: number
  stages: ReportHandlingStages
  /** 시간 오름차순. 같은 시각이면 놓은 사건이 받은 사건보다 앞에 온다. */
  events: ReportHandlingEvent[]
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
    // 관제센터 이름이 아니라 담당자 이름이다. assignedOperatorName 이 생기기 전에는
    // 센터 이름이 들어가 있어 목록의 '담당자' 열에 센터가 보였다.
    managerName: detail.assignedOperatorName ?? '미배정',
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

/**
 * 담당자용 처리 이력. 시민용 `/reports/{id}/status-history` 와 달리 상태를 3단계로 접지
 * 않고 담당자가 입력한 원본 사유를 그대로 준다. 조회 권한은 상세 조회와 같다.
 */
export async function getReportHandlingHistory(reportId: number) {
  if (USE_MOCK_API) return mockResponse(mockReportHandlingHistory(reportId))

  const response = await apiClient.get<ApiResponse<ReportHandlingHistory>>(
    `/operator/reports/${reportId}/handling-history`,
  )
  return response.data.data
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

/** 제보를 진짜 상황으로 승인해 시민 지도(마커·격자)에 반영한다. 배정·라이브는 유지된다. */
export async function confirmReport(reportId: number) {
  if (USE_MOCK_API) return mockResponse(undefined)

  await apiClient.patch(`/operator/reports/${reportId}/confirm`)
}

/** 대응을 마치고 상황을 종료한다. 지도 반영(공개)은 confirmReport 의 몫이다. */
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
