export type RiskLevel = 'safe' | 'caution' | 'warning' | 'danger'

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

export type ReviewStatus = 'pending' | 'approved' | 'rejected'

export type AiAnalysisStatus = 'queued' | 'analyzing' | 'detected' | 'completed'

export type WebRTCConnectionStatus = 'live' | 'waiting' | 'requested' | 'ended'

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
  riskLevel: RiskLevel
  riskScore: number
  reportedAt: string
  status: ReportStatus
  reviewStatus: ReviewStatus
  aiAnalysisStatus: AiAnalysisStatus
  managerName: string
  aiSummary: string
  riskTags: string[]
  history: AdminReportHistory[]
}

export interface WebRTCConnection {
  id: number
  reportId: number
  location: string
  riskLevel: RiskLevel
  riskScore: number
  connectedAt: string
  connectionStatus: WebRTCConnectionStatus
  quality: ConnectionQuality
  aiDetected: boolean
  aiAnalysisResult: string
  confidence: number
}

export interface RiskMarker {
  id: number
  location: string
  riskLevel: RiskLevel
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
