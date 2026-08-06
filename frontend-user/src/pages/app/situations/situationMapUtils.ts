import type { LiveReportSummary, RiskLevel } from './types'

// 소수 4자리(약 11m)까지 같으면 같은 위치의 제보로 묶어 마커 하나로 보여준다.
const GROUP_PRECISION = 4

const RISK_ORDER: Record<RiskLevel, number> = { critical: 5, high: 4, medium: 3, low: 2, very_low: 1 }

// 관리자 콘솔의 RISK_LEVEL_CONFIG markerColor 와 같은 색이다.
export const RISK_MARKER_COLORS: Record<RiskLevel, string> = {
  critical: '#dc2626',
  high: '#f97316',
  medium: '#eab308',
  low: '#16a34a',
  very_low: '#2563eb',
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
