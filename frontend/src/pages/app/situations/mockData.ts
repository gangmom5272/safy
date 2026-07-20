import type {
  AreaDetail,
  AreaMapData,
  AreaStatus,
  LiveReportSummary,
  LocationSearchResult,
  ReportAnalysis,
  ReportDetail,
  ReportSort,
  ReportType,
  ReportWebRtcInfo,
  RiskLevel,
} from './types'

export const mockLocations: LocationSearchResult[] = [
  {
    id: 'hongdae-station',
    name: '홍대입구역',
    address: '서울특별시 마포구 양화로 160',
    latitude: 37.5572,
    longitude: 126.9254,
  },
  {
    id: 'jamsil-sports-complex',
    name: '잠실종합운동장',
    address: '서울특별시 송파구 올림픽로 25',
    latitude: 37.515,
    longitude: 127.0729,
  },
  {
    id: 'gocheok-skydome',
    name: '고척스카이돔',
    address: '서울특별시 구로구 경인로 430',
    latitude: 37.4982,
    longitude: 126.8671,
  },
  {
    id: 'seoul-station',
    name: '서울역',
    address: '서울특별시 중구 한강대로 405',
    latitude: 37.5547,
    longitude: 126.9707,
  },
]

export const mockReports: LiveReportSummary[] = [
  {
    id: 'report-hongdae-crowd-live',
    title: '홍대입구역 9번 출구 인파 과밀',
    description: '인파가 너무 많아 이동이 매우 어렵습니다.',
    type: 'crowd',
    riskLevel: 'critical',
    locationName: '홍대입구역 9번 출구',
    address: '서울특별시 마포구 양화로 160',
    reportedAt: '2026-07-20T14:23:00+09:00',
    maskedThumbnailUrl: null,
    isWebRtc: true,
    isLive: true,
  },
  {
    id: 'report-road-accident',
    title: '도로 위 위험물로 차량 정체 발생',
    description: '차량 이동이 지연되고 있으니 우회가 필요합니다.',
    type: 'accident',
    riskLevel: 'high',
    locationName: '강남대로 교차로',
    address: '서울특별시 강남구 강남대로 396',
    reportedAt: '2026-07-20T14:08:00+09:00',
    maskedThumbnailUrl: null,
    isWebRtc: true,
    isLive: false,
  },
  {
    id: 'report-weather-flood',
    title: '지하보도 입구 침수 위험',
    description: '갑작스러운 비로 보행로 일부에 물이 고이고 있습니다.',
    type: 'flood_weather',
    riskLevel: 'medium',
    locationName: '잠실역 지하보도',
    address: '서울특별시 송파구 올림픽로 265',
    reportedAt: '2026-07-20T13:47:00+09:00',
    maskedThumbnailUrl: null,
    isWebRtc: false,
    isLive: false,
  },
  {
    id: 'report-facility-road',
    title: '시설물 파손으로 보행 주의 필요',
    description: '보도블록 일부가 파손되어 야간 보행 시 주의가 필요합니다.',
    type: 'facility_road',
    riskLevel: 'medium',
    locationName: '고척스카이돔 앞 보행로',
    address: '서울특별시 구로구 경인로 430',
    reportedAt: '2026-07-20T13:21:00+09:00',
    maskedThumbnailUrl: null,
    isWebRtc: false,
    isLive: false,
  },
  {
    id: 'report-etc-low',
    title: '역사 내부 안전 안내 요청',
    description: '이동 동선 안내가 부족해 일부 이용객이 혼란을 겪고 있습니다.',
    type: 'etc',
    riskLevel: 'low',
    locationName: '서울역 대합실',
    address: '서울특별시 중구 한강대로 405',
    reportedAt: '2026-07-20T12:58:00+09:00',
    maskedThumbnailUrl: null,
    isWebRtc: false,
    isLive: false,
  },
  {
    id: 'report-analysis-processing',
    title: '출입구 주변 혼잡 분석 진행 중',
    description: '관리자 마스킹 이후 AI 분석이 진행 중입니다.',
    type: 'crowd',
    riskLevel: 'high',
    locationName: '잠실종합운동장 3문',
    address: '서울특별시 송파구 올림픽로 25',
    reportedAt: '2026-07-20T12:42:00+09:00',
    maskedThumbnailUrl: null,
    isWebRtc: true,
    isLive: true,
  },
  {
    id: 'report-analysis-completed',
    title: '행사장 주변 인파 분석 완료',
    description: '인파 흐름이 느려 우회 동선 안내가 필요합니다.',
    type: 'crowd',
    riskLevel: 'critical',
    locationName: '고척스카이돔 1루 출입구',
    address: '서울특별시 구로구 경인로 430',
    reportedAt: '2026-07-20T12:17:00+09:00',
    maskedThumbnailUrl: null,
    isWebRtc: false,
    isLive: false,
  },
  {
    id: 'report-webrtc-ended',
    title: '실시간 제보 종료된 사고 현장',
    description: '현장 실시간 연결은 종료되었고 마스킹된 대표 이미지만 확인할 수 있습니다.',
    type: 'accident',
    riskLevel: 'high',
    locationName: '서울역 버스환승센터',
    address: '서울특별시 중구 한강대로 405',
    reportedAt: '2026-07-20T11:52:00+09:00',
    maskedThumbnailUrl: null,
    isWebRtc: true,
    isLive: false,
  },
  {
    id: 'report-webrtc-failed',
    title: '실시간 연결 실패 제보',
    description: '현장 네트워크 문제로 실시간 연결이 실패했습니다.',
    type: 'etc',
    riskLevel: 'low',
    locationName: '홍대입구역 내부',
    address: '서울특별시 마포구 양화로 160',
    reportedAt: '2026-07-20T11:30:00+09:00',
    maskedThumbnailUrl: null,
    isWebRtc: true,
    isLive: false,
  },
]

export const mockAreaStatus: AreaStatus = {
  riskLevel: 'critical',
  congestionLevel: 'very_crowded',
  recentReportCount: 18,
  weather: {
    condition: '흐림',
    temperature: 28,
  },
  updatedAt: '2026-07-20T14:25:00+09:00',
}

export const mockAreaMapData: AreaMapData = {
  center: { latitude: 37.5572, longitude: 126.9254 },
  zones: [
    {
      id: 'zone-hongdae-main',
      name: '인파 집중 구역',
      level: 'critical',
      coordinates: [
        { latitude: 37.5572, longitude: 126.9254 },
        { latitude: 37.5577, longitude: 126.9262 },
        { latitude: 37.5568, longitude: 126.9266 },
      ],
    },
    {
      id: 'zone-side-road',
      name: '우회 권장 구역',
      level: 'high',
      coordinates: [
        { latitude: 37.5565, longitude: 126.9239 },
        { latitude: 37.5569, longitude: 126.9244 },
        { latitude: 37.5561, longitude: 126.9248 },
      ],
    },
  ],
  reportMarkers: mockReports.slice(0, 5).map((report, index) => ({
    reportId: report.id,
    locationName: report.locationName,
    riskLevel: report.riskLevel,
    latitude: 37.5572 + index * 0.0008,
    longitude: 126.9254 - index * 0.0006,
  })),
}

export const mockReportDetails: ReportDetail[] = mockReports.map((report) => ({
  ...report,
  maskedMediaUrl: null,
}))

export const mockReportAnalyses: ReportAnalysis[] = [
  {
    reportId: 'report-hongdae-crowd-live',
    status: 'completed',
    congestionLevel: 'critical',
    estimatedPeopleCount: 920,
    aiSummary: '출구 주변 밀집도가 높고 이동 속도가 크게 저하되어 우회 안내가 필요합니다.',
    analyzedAt: '2026-07-20T14:26:00+09:00',
  },
  {
    reportId: 'report-road-accident',
    status: 'completed',
    congestionLevel: 'high',
    estimatedPeopleCount: 35,
    aiSummary: '차량 정체가 이어지고 있어 현장 통제와 우회 안내가 필요합니다.',
    analyzedAt: '2026-07-20T14:12:00+09:00',
  },
  {
    reportId: 'report-weather-flood',
    status: 'pending',
  },
  {
    reportId: 'report-analysis-processing',
    status: 'processing',
  },
  {
    reportId: 'report-analysis-completed',
    status: 'completed',
    congestionLevel: 'critical',
    estimatedPeopleCount: 1280,
    aiSummary: '행사장 출입구에 인파가 집중되어 분산 입장이 필요합니다.',
    analyzedAt: '2026-07-20T12:25:00+09:00',
  },
  {
    reportId: 'report-webrtc-failed',
    status: 'failed',
    errorMessage: '마스킹된 대표 이미지 품질이 낮아 분석을 완료하지 못했습니다.',
  },
]

export const mockWebRtcInfos: ReportWebRtcInfo[] = [
  {
    reportId: 'report-hongdae-crowd-live',
    roomId: 'room-hongdae-live',
    status: 'waiting',
  },
  {
    reportId: 'report-road-accident',
    roomId: 'room-road-accident',
    status: 'ended',
  },
  {
    reportId: 'report-analysis-processing',
    roomId: 'room-analysis-processing',
    status: 'connecting',
  },
  {
    reportId: 'report-webrtc-ended',
    roomId: 'room-ended',
    status: 'ended',
  },
  {
    reportId: 'report-webrtc-failed',
    roomId: 'room-failed',
    status: 'failed',
  },
]

export function filterMockLocations(keyword: string) {
  const normalizedKeyword = keyword.trim().toLowerCase()

  return mockLocations.filter((location) => {
    const target = `${location.name} ${location.address}`.toLowerCase()
    return target.includes(normalizedKeyword)
  })
}

export function getMockArea(areaId: string): AreaDetail {
  const location = mockLocations.find((item) => item.id === areaId) ?? mockLocations[0]

  return {
    id: location.id,
    name: location.name,
    address: location.address,
    latitude: location.latitude ?? 37.5572,
    longitude: location.longitude ?? 126.9254,
  }
}

export function getMockAreaMap(areaId: string): AreaMapData {
  const area = getMockArea(areaId)

  return {
    ...mockAreaMapData,
    center: {
      latitude: area.latitude,
      longitude: area.longitude,
    },
  }
}

export function getMockReportDetail(reportId: string) {
  return mockReportDetails.find((report) => report.id === reportId)
}

export function getMockReportAnalysis(reportId: string) {
  return mockReportAnalyses.find((analysis) => analysis.reportId === reportId)
}

export function getMockWebRtcInfo(reportId: string): ReportWebRtcInfo {
  return (
    mockWebRtcInfos.find((info) => info.reportId === reportId) ?? {
      reportId,
      status: 'none',
    }
  )
}

export function filterAndSortMockReports(types: ReportType[], sort: ReportSort) {
  const riskOrder: Record<RiskLevel, number> = {
    critical: 4,
    high: 3,
    medium: 2,
    low: 1,
  }

  const filteredReports = types.length
    ? mockReports.filter((report) => types.includes(report.type))
    : [...mockReports]

  return filteredReports.sort((a, b) => {
    if (sort === 'latest') {
      return new Date(b.reportedAt).getTime() - new Date(a.reportedAt).getTime()
    }

    const direction = sort === 'risk_desc' ? -1 : 1
    return (riskOrder[a.riskLevel] - riskOrder[b.riskLevel]) * direction
  })
}

export function filterAndSortMockAreaReports(areaId: string, types: ReportType[], sort: ReportSort) {
  const area = getMockArea(areaId)
  const areaKeyword = area.name.replace('역', '')

  return filterAndSortMockReports(types, sort).filter((report) => {
    const target = `${report.locationName} ${report.address ?? ''}`
    return target.includes(area.name) || target.includes(areaKeyword) || target.includes(area.address)
  })
}
