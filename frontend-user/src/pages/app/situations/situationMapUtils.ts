import type { LiveReportSummary, RiskLevel } from './types'

// 소수 4자리(약 11m)까지 같으면 같은 위치의 제보로 묶어 마커 하나로 보여준다.
const GROUP_PRECISION = 4

const RISK_ORDER: Record<RiskLevel, number> = { critical: 5, high: 4, medium: 3, low: 2, very_low: 1 }

// safy-user-v1 테마 색으로 갱신했다. 관리자 콘솔의 RISK_LEVEL_CONFIG markerColor 는
// 아직 이전 색을 쓰므로 두 화면의 위험도 색이 더 이상 같지 않다(관리자 쪽 재테마 전까지).
export const RISK_MARKER_COLORS: Record<RiskLevel, string> = {
  critical: '#C63B37',
  high: '#BB5A29',
  medium: '#B0781A',
  low: '#3B7F5B',
  very_low: '#2E6FE0',
}

export interface ReportMarkerGroup {
  key: string
  latitude: number
  longitude: number
  reports: LiveReportSummary[]
}

/** 좌표가 있는 제보만 같은 위치끼리 묶는다. */
export function groupReportsByLocation(reports: LiveReportSummary[]): ReportMarkerGroup[] {
  const groups = new Map<string, ReportMarkerGroup>()

  for (const report of reports) {
    if (report.latitude === undefined || report.longitude === undefined) continue

    const key = `${report.latitude.toFixed(GROUP_PRECISION)}:${report.longitude.toFixed(GROUP_PRECISION)}`
    const group = groups.get(key)
    if (group) {
      group.reports.push(report)
    } else {
      groups.set(key, {
        key,
        latitude: report.latitude,
        longitude: report.longitude,
        reports: [report],
      })
    }
  }

  return [...groups.values()]
}

export function highestRiskLevel(reports: LiveReportSummary[]): RiskLevel {
  // 시작값은 가장 낮은 등급이어야 한다. 'low' 로 두면 very_low 제보만 묶인 마커가
  // 한 단계 높게 칠해진다.
  return reports.reduce<RiskLevel>(
    (highest, report) => (RISK_ORDER[report.riskLevel] > RISK_ORDER[highest] ? report.riskLevel : highest),
    'very_low',
  )
}
