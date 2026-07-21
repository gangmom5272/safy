export type ReportType = 'crowd' | 'accident' | 'flood_weather' | 'facility_road' | 'etc'

export type ReportSort = 'latest' | 'risk_asc' | 'risk_desc'

export type RiskLevel = 'low' | 'medium' | 'high' | 'critical'

export type CongestionLevel = 'smooth' | 'normal' | 'crowded' | 'very_crowded'

export type AnalysisStatus = 'pending' | 'processing' | 'completed' | 'failed'

export type WebRtcStatus = 'none' | 'waiting' | 'connecting' | 'connected' | 'ended' | 'failed'

export type Coordinates = {
  latitude: number
  longitude: number
}

export type LocationSearchResult = {
  id: string
  name: string
  address: string
  latitude?: number
  longitude?: number
}

export type AreaDetail = {
  id: string
  name: string
  address: string
  latitude: number
  longitude: number
}

export type CongestionZone = {
  id: string
  name?: string
  level: RiskLevel
  coordinates: Coordinates[]
}

export type ReportMarker = {
  reportId: string
  latitude: number
  longitude: number
  locationName: string
  riskLevel: RiskLevel
}

export type AreaMapData = {
  center: Coordinates
  zones: CongestionZone[]
  reportMarkers: ReportMarker[]
}

export type AreaStatus = {
  riskLevel: RiskLevel
  congestionLevel: CongestionLevel
  recentReportCount: number
  weather?: {
    condition: string
    temperature?: number
  }
  updatedAt: string
}

export type ReportSource = 'webrtc' | 'photo'

export type LiveReportSummary = {
  id: string
  title: string
  type: ReportType
  locationName: string
  address?: string
  latitude?: number
  longitude?: number
  reportedAt: string
  relativeTime?: string
  riskLevel: RiskLevel
  description?: string
  maskedThumbnailUrl?: string | null
  isWebRtc: boolean
  isLive: boolean
  viewerCount?: number
  thumbnailType?: string
}

export type ReportDetail = LiveReportSummary & {
  maskedMediaUrl?: string | null
}

export type ReportAnalysis = {
  reportId: string
  status: AnalysisStatus
  congestionLevel?: RiskLevel
  estimatedPeopleCount?: number
  aiSummary?: string
  analyzedAt?: string
  errorMessage?: string
}

export type ReportWebRtcInfo = {
  reportId: string
  roomId?: string
  signalingUrl?: string
  statusSocketUrl?: string
  status: WebRtcStatus
  iceServers?: RTCIceServer[]
}

export type ReportStatusSocketMessage = {
  status?: WebRtcStatus
  isLive?: boolean
  message?: string
}

export type ReportListParams = {
  sort: ReportSort
  types: ReportType[]
  limit?: number
}

export type FavoritePlace = {
  id: string
  name: string
  searchQuery: string
  address: string
  latitude: number
  longitude: number
  savedAt: string
}

export const DEFAULT_REPORT_SORT: ReportSort = 'latest'

export const DEFAULT_REPORT_LIMIT = 20

export const reportTypeLabels: Record<ReportType, string> = {
  crowd: '인파 밀집',
  accident: '사고·위험',
  flood_weather: '침수·기상',
  facility_road: '시설물·도로',
  etc: '기타',
}

export const riskLevelLabels: Record<RiskLevel, string> = {
  critical: '위험도 매우 높음',
  high: '위험도 높음',
  medium: '위험도 보통',
  low: '위험도 낮음',
}

export const shortRiskLevelLabels: Record<RiskLevel, string> = {
  critical: '매우 높음',
  high: '높음',
  medium: '보통',
  low: '낮음',
}

export const congestionLevelLabels: Record<CongestionLevel, string> = {
  smooth: '여유',
  normal: '보통',
  crowded: '혼잡',
  very_crowded: '매우 혼잡',
}

export const reportSortLabels: Record<ReportSort, string> = {
  latest: '최신순',
  risk_asc: '위험도 낮은 순',
  risk_desc: '위험도 높은 순',
}

export const analysisStatusLabels: Record<AnalysisStatus, string> = {
  pending: '분석 대기 중',
  processing: '분석 진행 중',
  completed: '분석 완료',
  failed: '분석 실패',
}

export const webRtcStatusLabels: Record<WebRtcStatus, string> = {
  none: '연결 정보 없음',
  waiting: '연결 대기 중',
  connecting: '연결 중',
  connected: '연결됨',
  ended: '연결 종료',
  failed: '연결 실패',
}

export const reportTypes: ReportType[] = [
  'crowd',
  'accident',
  'flood_weather',
  'facility_road',
  'etc',
]

export type LiveReport = LiveReportSummary
