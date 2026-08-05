// 목 모드에서 API 대신 돌려줄 응답들.
// 화면용 목 데이터(adminMockData)를 백엔드 응답 형태로 옮겨 담는다.
import type { OperatorReportDetail, ReportAnalysis, TransferTarget } from '../pages/reports/reportApi'
import type { OperatorLiveSession } from '../pages/monitoring/monitoringApi'
import type { QueueItem } from '../pages/queue/queueApi'
import type { AdminReport, ReportType } from '../types/admin'
import { adminReports, mockPhoto } from './adminMockData'

type BackendCategory =
  | 'CROWD_CONGESTION' | 'ACCIDENT_RISK' | 'FLOOD_WEATHER' | 'FACILITY_ROAD' | 'OTHER'

const typeToCategory: Record<ReportType, BackendCategory> = {
  crowd: 'CROWD_CONGESTION',
  accident: 'ACCIDENT_RISK',
  flood_weather: 'FLOOD_WEATHER',
  facility_road: 'FACILITY_ROAD',
  etc: 'OTHER',
}

export function mockDashboardReports(size = 50): AdminReport[] {
  return adminReports.slice(0, size)
}

export function mockReportDetail(reportId: number): OperatorReportDetail {
  const report = adminReports.find((item) => item.id === reportId) ?? adminReports[0]

  return {
    reportId,
    reportType: report.imageUrl ? 'IMAGE' : 'LIVE',
    category: typeToCategory[report.type] ?? 'OTHER',
    subcategory: '기타',
    description: report.aiSummary,
    latitude: 37.5665,
    longitude: 126.978,
    address: report.location,
    status: 'IN_PROGRESS',
    routingStatus: 'ROUTED',
    administrativeAreaName: report.location,
    assignedControlCenter: { name: report.managerName, administrativeAreaName: report.location },
    reportedAt: report.reportedAt,
    // 목 모드에서도 "내 처리 결과"가 보이도록 제보 상태에 맞춰 배정 결과를 흉내낸다.
    myAssignmentStatus: report.status === 'completed'
      ? 'COMPLETED'
      : report.status === 'rejected' || report.status === 'cancelled' || report.status === 'reassigned'
        ? 'CANCELLED'
        : 'IN_PROGRESS',
    // 갤러리·확대 보기를 확인할 수 있도록 여러 장을 내려준다.
    images: report.imageUrl
      ? ['#334155', '#475569', '#64748b'].map((background, index) => ({
          reportImageId: index + 1,
          mediaAssetId: index + 1,
          displayOrder: index,
          storageStatus: 'AVAILABLE',
          previewUrl: index === 0 ? report.imageUrl! : mockPhoto(`현장 이미지 ${index + 1}`, background),
        }))
      : [],
  }
}

// 이관 모달의 세 가지 표시 상태(선택 가능·근무자 없음·거쳐와서 불가)를 모두 보여준다.
export function mockTransferTargets(): TransferTarget[] {
  return [
    {
      controlCenterId: 21,
      controlCenterName: '서초 관제센터',
      coverageRole: 'PRIMARY',
      priority: 2,
      hasOnDutyOperator: true,
      visited: false,
      transferable: true,
    },
    {
      controlCenterId: 22,
      controlCenterName: '송파 관제센터',
      coverageRole: 'BACKUP',
      priority: 3,
      hasOnDutyOperator: false,
      visited: false,
      transferable: true,
    },
    {
      controlCenterId: 23,
      controlCenterName: '관악 관제센터',
      coverageRole: 'BACKUP',
      priority: 4,
      hasOnDutyOperator: false,
      visited: true,
      transferable: false,
    },
  ]
}

export function mockReportAnalysis(reportId: number): ReportAnalysis {
  const report = adminReports.find((item) => item.id === reportId) ?? adminReports[0]

  return {
    reportId,
    status: 'COMPLETED',
    summary: report.aiSummary,
    riskLevel: typeof report.riskLevel === 'string' ? report.riskLevel : null,
    riskScore: report.riskScore ?? null,
    crowdCount: 120,
    confidence: 0.87,
    analyzedAt: report.reportedAt,
  }
}

// 라이브 세션은 라이브 제보(이미지가 없는 제보)에서 파생시킨다.
export function mockLiveSessions(): OperatorLiveSession[] {
  return adminReports
    .filter((report) => !report.imageUrl)
    .map((report) => ({
      streamRoomId: `mock-room-${report.id}`,
      reportId: report.id,
      status: 'LIVE' as const,
      category: typeToCategory[report.type] ?? 'OTHER',
      subcategory: '기타',
      description: report.aiSummary,
      latitude: 37.5665,
      longitude: 126.978,
      address: report.location,
      startedAt: report.reportedAt,
      endedAt: null,
      expiresAt: null,
      createdAt: report.reportedAt,
    }))
}

// 미배정 제보는 평소 비어 있는 게 정상이라, 목 모드에서는 화면 확인을 위해 세 건을 채운다.
// 이미지·라이브·위험도 없음 세 경우를 모두 보여준다.
export function mockQueueItems(): QueueItem[] {
  const minutesAgo = (minutes: number) => new Date(Date.now() - minutes * 60_000).toISOString()

  return [
    {
      reportId: 9001,
      reportType: 'IMAGE',
      category: 'CROWD_CONGESTION',
      subcategory: '보행 정체',
      description: '출구 앞 보행 흐름이 멈춰 있습니다.',
      latitude: 37.5563,
      longitude: 126.9236,
      address: '서울 마포구 홍대입구역 9번 출구',
      reportedAt: minutesAgo(97),
      image: {
        imageCount: 2,
        status: 'AVAILABLE',
        thumbnailUrl: mockPhoto('미배정 대기 제보', '#475569'),
        thumbnailUrlExpiresAt: null,
      },
      live: null,
      risk: { status: 'COMPLETED', score: 88, level: 'very_high', confidence: 0.87, confirmRequired: false, assessedAt: minutesAgo(95) },
    },
    {
      reportId: 9002,
      reportType: 'LIVE',
      category: 'ACCIDENT_RISK',
      subcategory: '시설 붕괴 우려',
      description: '가림막이 기울어져 있습니다.',
      latitude: 37.4979,
      longitude: 127.0276,
      address: '서울 강남구 강남역 11번 출구',
      reportedAt: minutesAgo(24),
      image: null,
      live: { streamRoomId: 'room-9002', status: 'LIVE', startedAt: minutesAgo(24), expiresAt: null },
      risk: { status: 'COMPLETED', score: 54, level: 'medium', confidence: 0.62, confirmRequired: true, assessedAt: minutesAgo(20) },
    },
    {
      reportId: 9003,
      reportType: 'IMAGE',
      category: 'FLOOD_WEATHER',
      subcategory: '도로 침수',
      description: null,
      latitude: 37.5172,
      longitude: 127.0473,
      address: '서울 강남구 삼성동 봉은사로',
      reportedAt: minutesAgo(6),
      image: { imageCount: 1, status: 'PENDING', thumbnailUrl: null, thumbnailUrlExpiresAt: null },
      live: null,
      risk: null,
    },
  ]
}
