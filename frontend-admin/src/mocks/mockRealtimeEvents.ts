// 개발용 수동 실시간 이벤트.
// 목 모드에는 SSE 서버가 없어 "신규 배정이 들어오는" 흐름을 확인할 수 없으므로,
// 브라우저 콘솔에서 window.safyMock.assignReport() 를 호출해 REPORT_ASSIGNED 를 흘려보낸다.
// 프로덕션 동작에 영향을 주는 자동 타이머는 두지 않는다.
import { emitRealtimeSignalForDev } from '../api/realtime'
import type { AdminReport } from '../types/admin'
import { adminReports, mockPhoto } from './adminMockData'

// 실제 좌표를 순서대로 돌려쓴다. 가짜 좌표를 무작위로 만들지 않는다.
const MOCK_LOCATIONS = [
  { location: '서울 종로구 광화문광장', latitude: 37.5759, longitude: 126.9769 },
  { location: '서울 송파구 잠실역 3번 출구', latitude: 37.5133, longitude: 127.1001 },
  { location: '서울 서대문구 신촌역 앞', latitude: 37.5551, longitude: 126.9368 },
] as const

let sequence = 0

/** 현재 담당자에게 새 제보가 배정된 상황을 흉내낸다. 생성된 제보를 돌려준다. */
export function assignMockReport(overrides: Partial<AdminReport> = {}): AdminReport {
  sequence += 1
  const spot = MOCK_LOCATIONS[(sequence - 1) % MOCK_LOCATIONS.length]
  const assignedAt = new Date().toISOString()

  const report: AdminReport = {
    id: 9000 + sequence,
    imageUrl: mockPhoto('실시간 배정 제보', '#1e293b'),
    location: spot.location,
    latitude: spot.latitude,
    longitude: spot.longitude,
    type: 'crowd',
    riskLevel: 'danger',
    riskScore: 90,
    reportedAt: assignedAt,
    assignedAt,
    verificationStatus: 'NEW',
    status: 'checking',
    reviewStatus: 'pending',
    aiAnalysisStatus: 'detected',
    managerName: '김관리',
    aiSummary: '실시간으로 배정된 목 제보입니다.',
    riskTags: ['실시간 배정'],
    history: [],
    ...overrides,
  }

  // 목 API 가 adminReports 를 그대로 읽으므로 재조회하면 이 제보가 최상단에 들어온다.
  adminReports.unshift(report)
  emitRealtimeSignalForDev({
    kind: 'event',
    event: {
      eventId: report.id,
      eventType: 'REPORT_ASSIGNED',
      reportId: report.id,
      resourceId: null,
      messageId: null,
      createdAt: assignedAt,
    },
  })

  return report
}

interface MockRealtimeWindow extends Window {
  safyMock?: { assignReport: typeof assignMockReport }
}

export function registerMockRealtimeHelpers() {
  if (!import.meta.env.DEV) return
  ;(window as MockRealtimeWindow).safyMock = { assignReport: assignMockReport }
}
