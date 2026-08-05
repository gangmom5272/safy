export type ReportType = 'crowd' | 'accident' | 'flood_weather' | 'facility_road' | 'etc'

export type ReportSort = 'latest' | 'risk_asc' | 'risk_desc'

/**
 * 백엔드 RiskLevel 5단계와 같은 축이다. `critical` 이 백엔드의 `very_high` 다 —
 * 화면 클래스명(risk-critical)이 이 이름을 쓰고 있어 그대로 둔다.
 */
export type RiskLevel = 'very_low' | 'low' | 'medium' | 'high' | 'critical'

/** 서울시 실시간 도시데이터의 인구 혼잡도 4단계. 백엔드 CongestionLevel 과 같은 축이다. */
export type CongestionLevel = 'relaxed' | 'normal' | 'slightly_crowded' | 'crowded'

/**
 * `not_assessable` 은 백엔드 RiskAssessmentStatus.NOT_ASSESSABLE 이다. 프레임이 전부
 * 무효·실패해서 평가할 수 없다고 <b>확정된</b> 상태이며 대기 중이 아니다. 예전에는 이 값을
 * 몰라서 pending 으로 떨어뜨려 "분석 대기 중"이 영구히 표시됐다.
 */
export type AnalysisStatus = 'pending' | 'processing' | 'completed' | 'not_assessable' | 'failed'

export type WebRtcStatus = 'none' | 'waiting' | 'connecting' | 'connected' | 'ended' | 'failed'

export type Coordinates = {
  latitude: number
  longitude: number
}

/**
 * 검색 결과의 종류. 상황 요약을 어디에 물을지 결정한다.
 *
 * - `area`: 자치구 → `/areas/{id}/status`
 * - `place`: 서울시 121장소 → `/places/{id}/status`
 * - `keyword`: 외부 검색으로 찾은 구체적 장소. 우리 식별자가 없어 좌표로 묻는다
 *   → `/locations/status?latitude=&longitude=`
 */
export type LocationResultType = 'area' | 'place' | 'keyword'

export type LocationSearchResult = {
  id: string
  type: LocationResultType
  name: string
  address: string
  latitude?: number
  longitude?: number
}

/**
 * 자치구 상세(주소 보강). {@link LocationSearchResult} 와 필드가 같아 상호 대체해서
 * 쓸 수 있게 `type: 'area'` 를 그대로 둔다 — 장소는 이 상세 조회 자체가 없다(검색
 * 결과가 곧 전체 정보다).
 */
export type AreaDetail = {
  type: 'area'
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
  // 지역 지도 응답에 함께 실려 오는 상황 요약. 직접 만든 지도 데이터에는 없을 수 있다.
  status?: AreaStatus
  center: Coordinates
  zones: CongestionZone[]
  reportMarkers: ReportMarker[]
}

/**
 * 서울시 실시간 도시데이터 관측값 하나. 자치구 상황 요약(가장 혼잡한 장소)과
 * 제보 분석(제보 시각의 그 지점)이 같은 모양을 쓴다.
 */
export type CongestionInfo = {
  placeName: string
  level: CongestionLevel
  /** 서울시 추정 인원 범위. 관측에 값이 없을 수 있다. */
  populationMin?: number
  populationMax?: number
  observedAt?: string
}

/**
 * 혼잡도 근거를 어떻게 찾았는지. 좌표 기준 조회에서만 채워진다 — 정확도가 다른 두 값을
 * 같은 문구로 보여주면 사용자가 구분할 수 없어 각주 표현을 바꾼다.
 *
 * - `grid`: 좌표가 속한 격자를 그 장소가 덮고 있었다
 * - `nearest`: 덮는 장소가 없어 가장 가까운 장소로 대체했다
 */
export type CongestionMatchType = 'grid' | 'nearest'

export type AreaStatus = {
  /** 근거가 없으면 비어 있다(121장소가 근처에 없거나 수집이 멈춘 경우). */
  congestion?: CongestionInfo
  /** 좌표 기준 조회에서만 온다. 자치구·121장소 조회는 기준 장소가 자명해 없다. */
  congestionMatchType?: CongestionMatchType
  recentReportCount: number
  /**
   * 최근 제보를 센 반경(m). 좌표 기준 조회에서만 온다 — 폴리곤이 없어 반경으로 세므로
   * 혼잡도와 범위가 달라 각주에 밝혀야 한다.
   */
  reportRadiusMeters?: number
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
  /**
   * AI가 계산한 국소 최대 혼잡도(stableL, 0~100). 프레임 L 을 평활한 뒤의 75백분위수라
   * 이름과 달리 최댓값이 아니다. 화면에서는 점수 대신 단계로 바꿔 보여준다.
   */
  localPeakCongestionScore?: number
  /** AI가 제보 영상을 분석해 낸 위험도 등급. */
  riskLevel?: RiskLevel
  /** 최종 위험 점수 0~100. 등급을 보조하는 값이다. */
  riskScore?: number
  /** 0~1. 프레임 수와 품질로 정해지는 평가 신뢰도. */
  confidence?: number
  // 백엔드는 confirmRequired 도 내려주지만 담지 않는다. 관제사가 검수 여부를 판단하는
  // 내부 신호라 시민이 그 값으로 할 일이 없다. 관리자 콘솔이 그쪽에서 쓴다.
  estimatedPeopleCount?: number
  aiSummary?: string
  analyzedAt?: string
  errorMessage?: string
}

export type ReportWebRtcInfo = {
  reportId: string
  roomId?: string
  roomName?: string
  status: WebRtcStatus
  // 서버가 계산한 입장 가능 여부. DISCONNECTED(재접속 대기)면 false 다.
  joinable: boolean
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
  very_low: '위험도 매우 낮음',
}

// 관리자 콘솔의 RISK_LEVEL_CONFIG 와 같은 표현을 쓴다. 같은 제보를 두 화면에서 다른
// 단어로 부르면 관제사와 시민이 서로 다른 이야기를 하게 된다.
export const shortRiskLevelLabels: Record<RiskLevel, string> = {
  critical: '매우 높음',
  high: '높음',
  medium: '보통',
  low: '낮음',
  very_low: '매우 낮음',
}

// 서울시가 쓰는 표현을 그대로 옮긴다. 임의로 '혼잡'·'매우 혼잡'으로 바꾸면 출처의
// 4단계와 화면의 단어가 어긋나 사용자가 다른 척도로 오해한다.
export const congestionLevelLabels: Record<CongestionLevel, string> = {
  relaxed: '여유',
  normal: '보통',
  slightly_crowded: '약간 붐빔',
  crowded: '붐빔',
}

/**
 * AI가 계산한 국소 최대 혼잡도(stableL)를 4단계로 나눈다.
 *
 * 경계는 새로 만들지 않고 기존 근거를 그대로 쓴다. `ai/docs/production-ai-analysis.md`
 * 8절의 칸별 점수(`cellScore = 인원 × 30`)에서 30은 칸당 1명, 60은 칸당 2명이고,
 * 80은 13절의 지속성·고위험 예외와 P2PNet 고밀도 게이트가 함께 쓰는 경계다.
 */
export function localPeakCongestionLevel(score: number): CongestionLevel {
  if (score >= 80) return 'crowded'
  if (score >= 60) return 'slightly_crowded'
  if (score >= 30) return 'normal'
  return 'relaxed'
}

// 위험도 배지와 같은 톤 클래스를 재사용한다(초록 → 파랑 → 주황 → 빨강).
export const congestionLevelTones: Record<CongestionLevel, string> = {
  relaxed: 'low',
  normal: 'medium',
  slightly_crowded: 'high',
  crowded: 'critical',
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
  not_assessable: '분석 불가',
  failed: '분석 실패',
}

export const reportTypes: ReportType[] = [
  'crowd',
  'accident',
  'flood_weather',
  'facility_road',
  'etc',
]

export type LiveReport = LiveReportSummary
