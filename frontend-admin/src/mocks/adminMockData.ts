import type {
  AdminReport,
  AiAnalysisStatus,
  ConnectionQuality,
  ReportStatus,
  ReportType,
  ReviewStatus,
  RiskMarker,
  DashboardStats,
  SystemStatus,
  WebRTCConnection,
  WebRTCConnectionStatus,
} from '../types/admin'

export const reportTypeLabels: Record<ReportType, string> = {
  crowd: '인파',
  accident: '사고',
  flood_weather: '침수/기상',
  facility_road: '시설/도로',
  etc: '기타',
}

export const reportStatusLabels: Record<ReportStatus, string> = {
  received: '접수',
  checking: '확인 중',
  in_progress: '처리 중',
  completed: '처리 완료',
  rejected: '반려',
  cancelled: '취소',
  reassigned: '재배정',
}

export const reviewStatusLabels: Record<ReviewStatus, string> = {
  pending: '검토 전',
  approved: '승인',
  rejected: '반려',
}

export const aiAnalysisStatusLabels: Record<AiAnalysisStatus, string> = {
  queued: '대기',
  analyzing: '분석 중',
  detected: '위험 감지',
  completed: '분석 완료',
}

export const connectionStatusLabels: Record<WebRTCConnectionStatus, string> = {
  live: '연결 중',
  waiting: '대기',
  requested: '요청됨',
  ended: '종료',
}

export const connectionQualityLabels: Record<ConnectionQuality, string> = {
  good: '양호',
  normal: '보통',
  poor: '불안정',
}

export const emergencyBanner =
  '긴급재난명령 수신 · 서울 마포구 홍대입구역 인근 집회 발생 중'

export const systemStatus: SystemStatus = {
  serverStatus: '정상 운영',
  webrtcConnections: 2,
  activeReports: 14,
  aiAnalysisStatus: '정상 분석 중',
  lastUpdatedAt: '2026-07-23 14:25',
}

export const dashboardStats: DashboardStats = {
  totalReports: 847,
  dangerReports: 14,
  webrtcConnections: 2,
  highRiskAreas: 5,
}

// 목 모드에서 제보 이미지 갤러리·확대 보기를 확인할 수 있도록 하는 자리표시 이미지.
// 외부 요청 없이 렌더되도록 data URI 로 넣는다.
export function mockPhoto(label: string, background: string) {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="640" height="480">`
    + `<rect width="640" height="480" fill="${background}"/>`
    + `<text x="320" y="240" font-family="sans-serif" font-size="28" fill="#ffffff"`
    + ` text-anchor="middle" dominant-baseline="middle">${label}</text></svg>`
  return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`
}

export const adminReports: AdminReport[] = [
  {
    id: 1,
    isLive: false,
    imageUrl: mockPhoto('현장 이미지 1', '#334155'),
    location: '서울 마포구 홍대입구역 9번 출구',
    latitude: 37.5572,
    longitude: 126.9254,
    type: 'crowd',
    riskLevel: 'danger',
    riskScore: 92,
    reportedAt: '2026-07-23 14:18',
    // 관리자 확인 상태는 처리 상태와 별개다. 목 데이터에도 신규/확인을 섞어 둔다.
    verificationStatus: 'NEW',
    status: 'checking',
    reviewStatus: 'pending',
    aiAnalysisStatus: 'detected',
    managerName: '김관리',
    aiSummary: '밀집 인원이 빠르게 증가하고 있으며 보행 흐름 정체가 감지되었습니다.',
    riskTags: ['고밀집', '보행 정체', '현장 확인 필요'],
    history: [
      { id: 1, action: '제보 접수', actor: '시스템', createdAt: '2026-07-23 14:18' },
      { id: 2, action: 'AI 위험 감지', actor: 'SAFY AI', createdAt: '2026-07-23 14:19' },
    ],
  },
  {
    id: 2,
    isLive: true,
    location: '서울 강남구 강남역 교차로',
    latitude: 37.4979,
    longitude: 127.0276,
    type: 'accident',
    riskLevel: 'warning',
    riskScore: 78,
    reportedAt: '2026-07-23 14:02',
    verificationStatus: 'CHECKED',
    status: 'in_progress',
    reviewStatus: 'approved',
    aiAnalysisStatus: 'completed',
    managerName: '박담당',
    aiSummary: '차량 접촉 사고로 추정되는 현장 정체가 확인되었습니다.',
    riskTags: ['교통 정체', '차량 사고'],
    history: [
      { id: 1, action: '제보 접수', actor: '시스템', createdAt: '2026-07-23 14:02' },
      { id: 2, action: '담당자 배정', actor: '박담당', createdAt: '2026-07-23 14:05' },
    ],
  },
  {
    id: 3,
    isLive: true,
    location: '부산 해운대구 해운대해수욕장',
    latitude: 35.1587,
    longitude: 129.1604,
    type: 'crowd',
    riskLevel: 'warning',
    riskScore: 74,
    reportedAt: '2026-07-23 13:48',
    verificationStatus: 'NEW',
    status: 'received',
    reviewStatus: 'pending',
    aiAnalysisStatus: 'analyzing',
    managerName: '이운영',
    aiSummary: '해변 출입구 부근 인파 밀집 가능성이 분석 중입니다.',
    riskTags: ['행락 인파', '분석 중'],
    history: [{ id: 1, action: '제보 접수', actor: '시스템', createdAt: '2026-07-23 13:48' }],
  },
  {
    id: 4,
    isLive: true,
    location: '대전 유성구 갑천 산책로',
    latitude: 36.3620,
    longitude: 127.3560,
    type: 'flood_weather',
    riskLevel: 'caution',
    riskScore: 56,
    reportedAt: '2026-07-23 13:22',
    verificationStatus: 'CHECKED',
    status: 'completed',
    reviewStatus: 'approved',
    aiAnalysisStatus: 'completed',
    managerName: '정상황',
    aiSummary: '하천 수위 상승은 확인되나 즉시 통제 수준은 아닙니다.',
    riskTags: ['수위 상승', '주의 관찰'],
    history: [
      { id: 1, action: '제보 접수', actor: '시스템', createdAt: '2026-07-23 13:22' },
      { id: 2, action: '처리 완료', actor: '정상황', createdAt: '2026-07-23 13:45' },
    ],
  },
  {
    id: 5,
    isLive: true,
    location: '인천 중구 월미도 문화거리',
    latitude: 37.4725,
    longitude: 126.5977,
    type: 'facility_road',
    riskLevel: 'danger',
    riskScore: 88,
    reportedAt: '2026-07-23 12:58',
    verificationStatus: 'CHECKED',
    status: 'rejected',
    reviewStatus: 'approved',
    aiAnalysisStatus: 'detected',
    managerName: '최현장',
    aiSummary: '도로 시설물 파손과 보행자 우회가 동시에 감지되었습니다.',
    riskTags: ['시설물 파손', '보행 위험'],
    history: [
      { id: 1, action: '제보 접수', actor: '시스템', createdAt: '2026-07-23 12:58' },
      { id: 2, action: '현장 조치 요청', actor: '최현장', createdAt: '2026-07-23 13:08' },
    ],
  },
  {
    id: 6,
    isLive: true,
    location: '광주 동구 충장로',
    latitude: 35.1490,
    longitude: 126.9199,
    type: 'etc',
    riskLevel: 'safe',
    riskScore: 22,
    reportedAt: '2026-07-23 12:30',
    verificationStatus: 'CHECKED',
    status: 'cancelled',
    reviewStatus: 'rejected',
    aiAnalysisStatus: 'completed',
    managerName: '오검토',
    aiSummary: '반복 제보와 현장 영상 기준 위험 징후가 낮습니다.',
    riskTags: ['중복 제보', '위험 낮음'],
    history: [
      { id: 1, action: '제보 접수', actor: '시스템', createdAt: '2026-07-23 12:30' },
      { id: 2, action: '반려', actor: '오검토', createdAt: '2026-07-23 12:41' },
    ],
  },
]

export const webrtcConnections: WebRTCConnection[] = [
  {
    id: '101',
    reportId: 1,
    location: '서울 마포구 홍대입구역 9번 출구',
    riskLevel: 'danger',
    riskScore: 92,
    connectedAt: '2026-07-23 14:21',
    connectionStatus: 'live',
    quality: 'good',
    aiDetected: true,
    aiAnalysisResult: '인파 밀집과 이동 정체가 동시에 감지되었습니다.',
    confidence: 94,
  },
  {
    id: '102',
    reportId: 5,
    location: '인천 중구 월미도 문화거리',
    riskLevel: 'danger',
    riskScore: 88,
    connectedAt: '2026-07-23 13:11',
    connectionStatus: 'live',
    quality: 'normal',
    aiDetected: true,
    aiAnalysisResult: '보행 구간 장애물이 확인되었습니다.',
    confidence: 87,
  },
  {
    id: '103',
    reportId: 2,
    location: '서울 강남구 강남역 교차로',
    riskLevel: 'warning',
    riskScore: 78,
    connectedAt: '2026-07-23 14:07',
    connectionStatus: 'waiting',
    quality: 'normal',
    aiDetected: false,
    aiAnalysisResult: '추가 영상 연결 대기 중입니다.',
    confidence: 61,
  },
  {
    id: '104',
    reportId: 3,
    location: '부산 해운대구 해운대해수욕장',
    riskLevel: 'warning',
    riskScore: 74,
    connectedAt: '2026-07-23 13:52',
    connectionStatus: 'waiting',
    quality: 'poor',
    aiDetected: true,
    aiAnalysisResult: '출입구 주변 밀집 가능성이 있습니다.',
    confidence: 79,
  },
  {
    id: '105',
    reportId: 4,
    location: '대전 유성구 갑천 산책로',
    riskLevel: 'caution',
    riskScore: 56,
    connectedAt: '2026-07-23 13:26',
    connectionStatus: 'ended',
    quality: 'good',
    aiDetected: false,
    aiAnalysisResult: '주의 수준으로 모니터링 종료되었습니다.',
    confidence: 72,
  },
  {
    id: '106',
    reportId: 6,
    location: '광주 동구 충장로',
    riskLevel: 'safe',
    riskScore: 22,
    connectedAt: '2026-07-23 12:34',
    connectionStatus: 'ended',
    quality: 'good',
    aiDetected: false,
    aiAnalysisResult: '위험 징후가 낮습니다.',
    confidence: 68,
  },
]

export const riskMarkers: RiskMarker[] = [
  { id: 1, location: '홍대입구역', riskLevel: 'danger', riskScore: 92, top: 35, left: 42 },
  { id: 2, location: '강남역', riskLevel: 'warning', riskScore: 78, top: 52, left: 58 },
  { id: 3, location: '해운대', riskLevel: 'warning', riskScore: 74, top: 66, left: 72 },
  { id: 4, location: '갑천', riskLevel: 'caution', riskScore: 56, top: 45, left: 28 },
  { id: 5, location: '월미도', riskLevel: 'danger', riskScore: 88, top: 26, left: 18 },
]
