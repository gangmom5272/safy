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

const riskOrder: Record<RiskLevel, number> = {
  critical: 4,
  high: 3,
  medium: 2,
  low: 1,
}

export const mockLocations: LocationSearchResult[] = [
  {
    id: 'hongdae-station',
    name: '홍대입구역',
    address: '서울특별시 마포구 양화로 160',
    latitude: 37.5572,
    longitude: 126.9254,
  },
  {
    id: 'gangnam-station',
    name: '강남역',
    address: '서울특별시 강남구 강남대로 396',
    latitude: 37.4979,
    longitude: 127.0276,
  },
  {
    id: 'sinnonhyeon-station',
    name: '신논현역',
    address: '서울특별시 강남구 봉은사로 102',
    latitude: 37.5046,
    longitude: 127.0255,
  },
  {
    id: 'cgv-gangnam',
    name: 'CGV 강남',
    address: '서울특별시 강남구 강남대로 438',
    latitude: 37.5016,
    longitude: 127.0263,
  },
  {
    id: 'jamsil-station',
    name: '잠실역',
    address: '서울특별시 송파구 올림픽로 265',
    latitude: 37.5133,
    longitude: 127.1002,
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
    title: '홍대입구역 9번 출구 인근 인파 과밀',
    description:
      '인파가 너무 몰려서 9번 출구 앞이 완전히 막혔어요. 이동이 매우 어려운 상황입니다. 우회로를 이용해 주세요.',
    type: 'crowd',
    riskLevel: 'critical',
    locationName: '홍대입구역 9번 출구 앞',
    address: '서울특별시 마포구 양화로 160',
    latitude: 37.5575,
    longitude: 126.9251,
    reportedAt: '2026-07-21T09:38:00+09:00',
    relativeTime: '3분 전',
    maskedThumbnailUrl: null,
    isWebRtc: true,
    isLive: true,
    viewerCount: 128,
    thumbnailType: 'crowd',
  },
  {
    id: 'report-gangnam-crowd-live',
    title: '강남역 출구 앞 인파 밀집',
    description: '강남역 11번 출구 앞 대기 줄이 길어져 보행자 동선이 좁아졌습니다.',
    type: 'crowd',
    riskLevel: 'high',
    locationName: '강남역 11번 출구',
    address: '서울특별시 강남구 강남대로 396',
    latitude: 37.4982,
    longitude: 127.0278,
    reportedAt: '2026-07-21T09:31:00+09:00',
    relativeTime: '10분 전',
    maskedThumbnailUrl: null,
    isWebRtc: true,
    isLive: true,
    viewerCount: 84,
    thumbnailType: 'live',
  },
  {
    id: 'report-sinnonhyeon-accident',
    title: '신논현역 인근 도로 사고',
    description: '신논현역 사거리 인근 접촉 사고로 우측 차로 통행이 지연되고 있습니다.',
    type: 'accident',
    riskLevel: 'high',
    locationName: '신논현역 사거리',
    address: '서울특별시 강남구 봉은사로 102',
    latitude: 37.5044,
    longitude: 127.0252,
    reportedAt: '2026-07-21T09:22:00+09:00',
    relativeTime: '19분 전',
    maskedThumbnailUrl: null,
    isWebRtc: false,
    isLive: false,
    viewerCount: 42,
    thumbnailType: 'road',
  },
  {
    id: 'report-jamsil-event-crowd',
    title: '잠실역 행사 종료 후 혼잡',
    description: '행사 종료 인파가 잠실역 방향으로 몰리며 개찰구 주변 대기가 발생했습니다.',
    type: 'crowd',
    riskLevel: 'medium',
    locationName: '잠실역 2번 출구',
    address: '서울특별시 송파구 올림픽로 265',
    latitude: 37.513,
    longitude: 127.1005,
    reportedAt: '2026-07-21T09:04:00+09:00',
    relativeTime: '37분 전',
    maskedThumbnailUrl: null,
    isWebRtc: true,
    isLive: false,
    viewerCount: 67,
    thumbnailType: 'event',
  },
  {
    id: 'report-seoul-station-facility',
    title: '서울역 시설물 파손',
    description: '서울역 대합실 연결 통로의 안내 표지 일부가 파손되어 보행 주의가 필요합니다.',
    type: 'facility_road',
    riskLevel: 'medium',
    locationName: '서울역 대합실',
    address: '서울특별시 중구 한강대로 405',
    latitude: 37.5549,
    longitude: 126.9704,
    reportedAt: '2026-07-21T08:48:00+09:00',
    relativeTime: '53분 전',
    maskedThumbnailUrl: null,
    isWebRtc: false,
    isLive: false,
    viewerCount: 21,
    thumbnailType: 'facility',
  },
  {
    id: 'report-gangnam-weather-flood',
    title: '강남역 인근 침수 위험',
    description: '강한 비로 CGV 강남 앞 보행로 배수구 주변에 물이 고이고 있습니다.',
    type: 'flood_weather',
    riskLevel: 'critical',
    locationName: 'CGV 강남 앞',
    address: '서울특별시 강남구 강남대로 438',
    latitude: 37.5016,
    longitude: 127.0263,
    reportedAt: '2026-07-21T08:35:00+09:00',
    relativeTime: '1시간 전',
    maskedThumbnailUrl: null,
    isWebRtc: false,
    isLive: false,
    viewerCount: 96,
    thumbnailType: 'weather',
  },
  {
    id: 'report-gangnam-etc',
    title: '강남역 안내 방송 요청',
    description: '출구별 혼잡도가 달라 현장 안내 방송이 필요해 보입니다.',
    type: 'etc',
    riskLevel: 'low',
    locationName: '강남역 지하상가',
    address: '서울특별시 강남구 강남대로 396',
    latitude: 37.4977,
    longitude: 127.028,
    reportedAt: '2026-07-21T08:10:00+09:00',
    relativeTime: '1시간 전',
    maskedThumbnailUrl: null,
    isWebRtc: false,
    isLive: false,
    viewerCount: 13,
    thumbnailType: 'etc',
  },
]

export const mockAreaStatus: AreaStatus = {
  riskLevel: 'high',
  congestionLevel: 'crowded',
  recentReportCount: 6,
  liveReportCount: 2,
  weather: {
    condition: '흐림',
    temperature: 28,
  },
  updatedAt: '2026-07-21T09:41:00+09:00',
}

export const mockReportDetails: ReportDetail[] = mockReports.map((report) => ({
  ...report,
  maskedMediaUrl: null,
}))

export const mockReportAnalyses: ReportAnalysis[] = mockReports.map((report) => ({
  reportId: report.id,
  status: report.riskLevel === 'low' ? 'pending' : 'completed',
  congestionLevel: report.riskLevel,
  riskScore: { critical: 88, high: 72, medium: 51, low: 18 }[report.riskLevel],
  estimatedPeopleCount: report.viewerCount ? report.viewerCount * 8 : undefined,
  aiSummary: `${report.locationName} 주변 상황을 목업 데이터로 표시하고 있습니다.`,
  analyzedAt: '2026-07-21T09:42:00+09:00',
}))

export const mockWebRtcInfos: ReportWebRtcInfo[] = mockReports.map((report) => ({
  reportId: report.id,
  roomId: report.isWebRtc ? `mock-room-${report.id}` : undefined,
  status: report.isWebRtc ? (report.isLive ? 'waiting' : 'ended') : 'none',
  joinable: report.isWebRtc && report.isLive,
}))

function normalize(value: string) {
  return value.trim().toLowerCase()
}

function compact(value: string) {
  return normalize(value).replace(/\s/g, '')
}

function matchesKeyword(target: string, keyword: string) {
  const normalizedKeyword = normalize(keyword)
  const compactKeyword = compact(keyword)

  if (!normalizedKeyword) {
    return true
  }

  return normalize(target).includes(normalizedKeyword) || compact(target).includes(compactKeyword)
}

export function filterMockLocations(keyword: string): LocationSearchResult[] {
  return mockLocations.filter((location) =>
    matchesKeyword(`${location.name} ${location.address}`, keyword),
  )
}

export function getBestMockLocation(keyword: string): LocationSearchResult | null {
  const matches = filterMockLocations(keyword)

  if (matches.length) {
    return matches[0]
  }

  const report = mockReports.find((item) =>
    matchesKeyword(`${item.title} ${item.description ?? ''} ${item.locationName} ${item.address ?? ''}`, keyword),
  )

  if (!report) {
    return null
  }

  return {
    id: `search-${compact(keyword)}`,
    name: keyword.trim(),
    address: report.address ?? report.locationName,
    latitude: report.latitude,
    longitude: report.longitude,
  }
}

export function getMockArea(areaId: string): AreaDetail {
  const location = mockLocations.find((item) => item.id === areaId) ?? mockLocations[0]

  return {
    id: location.id,
    name: location.name,
    address: location.address,
    latitude: location.latitude ?? 37.4979,
    longitude: location.longitude ?? 127.0276,
  }
}

export function getMockAreaByQuery(keyword: string): AreaDetail | null {
  const location = getBestMockLocation(keyword)

  if (!location) {
    return null
  }

  return {
    id: location.id,
    name: location.name,
    address: location.address,
    latitude: location.latitude ?? 37.4979,
    longitude: location.longitude ?? 127.0276,
  }
}

export function getMockAreaMap(areaIdOrQuery: string): AreaMapData {
  const area = getMockAreaByQuery(areaIdOrQuery) ?? getMockArea(areaIdOrQuery)
  const reports = filterMockReportsByKeyword(area.name)
  const markers = (reports.length ? reports : mockReports.slice(0, 4)).map((report, index) => ({
    reportId: report.id,
    locationName: report.locationName,
    riskLevel: report.riskLevel,
    latitude: report.latitude ?? area.latitude + index * 0.0007,
    longitude: report.longitude ?? area.longitude - index * 0.0006,
  }))

  return {
    center: { latitude: area.latitude, longitude: area.longitude },
    zones: [
      {
        id: `${area.id}-critical-zone`,
        name: '매우 혼잡',
        level: 'critical',
        coordinates: [
          { latitude: area.latitude, longitude: area.longitude },
          { latitude: area.latitude + 0.0008, longitude: area.longitude + 0.0008 },
          { latitude: area.latitude - 0.0006, longitude: area.longitude + 0.0005 },
        ],
      },
      {
        id: `${area.id}-crowded-zone`,
        name: '혼잡',
        level: 'high',
        coordinates: [
          { latitude: area.latitude + 0.001, longitude: area.longitude - 0.001 },
          { latitude: area.latitude + 0.0014, longitude: area.longitude - 0.0004 },
        ],
      },
      {
        id: `${area.id}-normal-zone`,
        name: '보통',
        level: 'medium',
        coordinates: [{ latitude: area.latitude - 0.001, longitude: area.longitude - 0.001 }],
      },
    ],
    reportMarkers: markers,
  }
}

export function getMockReportDetail(reportId: string) {
  return mockReportDetails.find((report) => report.id === reportId)
}

export function getMockReportAnalysis(reportId: string) {
  return mockReportAnalyses.find((analysis) => analysis.reportId === reportId)
}

export function getMockWebRtcInfo(reportId: string): ReportWebRtcInfo {
  return mockWebRtcInfos.find((info) => info.reportId === reportId)
    ?? { reportId, status: 'none', joinable: false }
}

export function filterMockReportsByKeyword(keyword: string): LiveReportSummary[] {
  return mockReports.filter((report) =>
    matchesKeyword(
      `${report.title} ${report.description ?? ''} ${report.locationName} ${report.address ?? ''}`,
      keyword,
    ),
  )
}

export function filterAndSortMockReports(types: ReportType[], sort: ReportSort) {
  return sortReports(types.length ? mockReports.filter((report) => types.includes(report.type)) : mockReports, sort)
}

export function filterAndSortMockAreaReports(areaIdOrQuery: string, types: ReportType[], sort: ReportSort) {
  const area = getMockAreaByQuery(areaIdOrQuery) ?? getMockArea(areaIdOrQuery)
  const areaKeyword = area.name.replace(/역$/, '')
  const reports = mockReports.filter((report) => {
    const target = `${report.title} ${report.description ?? ''} ${report.locationName} ${report.address ?? ''}`
    return matchesKeyword(target, area.name) || matchesKeyword(target, areaKeyword)
  })
  const filtered = types.length ? reports.filter((report) => types.includes(report.type)) : reports

  return sortReports(filtered, sort)
}

function sortReports(reports: LiveReportSummary[], sort: ReportSort) {
  return [...reports].sort((a, b) => {
    if (sort === 'latest') {
      return new Date(b.reportedAt).getTime() - new Date(a.reportedAt).getTime()
    }

    const diff = riskOrder[b.riskLevel] - riskOrder[a.riskLevel]
    return sort === 'risk_desc' ? diff : -diff
  })
}
