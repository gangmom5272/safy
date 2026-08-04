export type RiskLevel = 'very_high' | 'high' | 'medium' | 'low' | 'very_low'

export type LegacyRiskLevel = 'danger' | 'warning' | 'caution' | 'safe'

export type RiskLevelValue = RiskLevel | LegacyRiskLevel

export type ReportType =
  | 'crowd'
  | 'accident'
  | 'flood_weather'
  | 'facility_road'
  | 'etc'

export type ReportStatus =
  | 'received'
  | 'checking'
  | 'in_progress'
  | 'completed'
  | 'rejected'
  | 'cancelled'
  | 'reassigned'

export type ReviewStatus = 'pending' | 'approved' | 'rejected'

/**
 * 관리자 확인 상태. "담당자가 이 제보를 열어봤는가"만 나타낸다.
 * ReportStatus(접수·확인 중·처리 중·완료…)와는 다른 축이므로 섞어 쓰지 않는다.
 */
export type VerificationStatus = 'NEW' | 'CHECKED'

export type AiAnalysisStatus = 'queued' | 'analyzing' | 'detected' | 'completed'

export type WebRTCConnectionStatus = 'live' | 'waiting' | 'requested' | 'ended'

export type LiveStreamStatus = 'CONNECTING' | 'LIVE' | 'DISCONNECTED' | 'ENDED'

export type ConnectionQuality = 'good' | 'normal' | 'poor'

export interface AdminReportHistory {
  id: number
  action: string
  actor: string
  createdAt: string
}

export interface AdminReport {
  id: number
  location: string
  type: ReportType
  subcategory?: string
  // 백엔드가 아직 내려주지 않는 값은 null 로 두고 화면에서 '-' 로 표시한다.
  riskLevel: RiskLevelValue | null
  riskScore: number | null
  reportedAt: string
  status: ReportStatus
  // 처리 상태와 별개인 관리자 확인 상태. 확인 여부를 알 수 없는 응답에서는 비어 있다.
  verificationStatus?: VerificationStatus
  // 배정 시각. reportedAt 과 달리 "내게 넘어온 시점"이라 최근 배정 정렬 기준으로 쓴다.
  assignedAt?: string | null
  reviewStatus: ReviewStatus | null
  aiAnalysisStatus: AiAnalysisStatus | null
  managerName: string
  aiSummary: string
  riskTags: string[]
  history: AdminReportHistory[]
  reporterName?: string
  imageUrl?: string
  imageCount?: number
  mediaStatus?: string | null
  aiIndicators?: string[]
  aiCompletedAt?: string
  // 지도 표시용. 담당 이력 목록 응답에는 좌표가 없어 비어 있을 수 있다.
  latitude?: number | null
  longitude?: number | null
  assignmentId?: number
  // 담당 이력 목록에서만 채워진다. 배정이 어떻게 끝났는지(종료·반려·재배정) 구분용.
  assignmentStatus?: 'ASSIGNED' | 'ACCEPTED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'
  completedAt?: string | null
  isLive?: boolean
  streamRoomId?: string | null
  liveStatus?: LiveStreamStatus | null
  controlCenterName?: string | null
}

export interface WebRTCConnection {
  // 백엔드 streamRoomId 는 UUID 문자열이다.
  id: string
  reportId: number
  location: string
  riskLevel: RiskLevelValue | null
  riskScore: number | null
  connectedAt: string
  connectionStatus: WebRTCConnectionStatus
  quality: ConnectionQuality | null
  aiDetected: boolean
  aiAnalysisResult: string
  confidence: number | null
}

export interface RiskMarker {
  id: number
  location: string
  riskLevel: RiskLevelValue
  riskScore: number
  top: number
  left: number
}

export interface SystemStatus {
  serverStatus: string
  webrtcConnections: number
  activeReports: number
  aiAnalysisStatus: string
  lastUpdatedAt: string
}

export interface DashboardStats {
  totalReports: number
  dangerReports: number
  webrtcConnections: number
  highRiskAreas: number
}
