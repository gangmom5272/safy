// 목 모드에서 API 대신 돌려줄 응답들.
// 화면용 목 데이터(adminMockData)를 백엔드 응답 형태로 옮겨 담는다.
import type { OperatorReportDetail, ReportAnalysis } from '../pages/reports/reportApi'
import type { OperatorLiveSession } from '../pages/monitoring/monitoringApi'
import type { AdminReport, ReportType } from '../types/admin'
import { adminReports } from './adminMockData'

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
    images: report.imageUrl
      ? [{ reportImageId: 1, mediaAssetId: 1, displayOrder: 0, storageStatus: 'AVAILABLE', previewUrl: report.imageUrl }]
      : [],
  }
}

export function mockReportAnalysis(reportId: number): ReportAnalysis {
  const report = adminReports.find((item) => item.id === reportId) ?? adminReports[0]

  return {
    reportId,
    status: 'COMPLETED',
    summary: report.aiSummary,
    riskLevel: typeof report.riskLevel === 'string' ? report.riskLevel : null,
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
