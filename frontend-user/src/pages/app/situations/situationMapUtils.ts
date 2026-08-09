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

/** 아직 평가되지 않은 제보의 색. 관리자 콘솔의 UNCLASSIFIED_RISK_COLOR 와 같은 회색이다. */
export const UNASSESSED_RISK_COLOR = '#94a3b8'

/** 평가 전 제보를 등급 색으로 칠하면 '미평가'가 특정 등급으로 읽힌다. */
export function riskMarkerColor(level?: RiskLevel) {
  return level ? RISK_MARKER_COLORS[level] : UNASSESSED_RISK_COLOR
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

/**
 * 묶인 제보 중 가장 높은 등급. 평가된 제보가 하나도 없으면 undefined 다 —
 * 그때 최저 등급을 돌려주면 미평가 마커가 '매우 낮음' 색으로 칠해진다.
 */
export function highestRiskLevel(reports: LiveReportSummary[]): RiskLevel | undefined {
  return reports.reduce<RiskLevel | undefined>((highest, report) => {
    if (!report.riskLevel) return highest
    if (!highest) return report.riskLevel
    return RISK_ORDER[report.riskLevel] > RISK_ORDER[highest] ? report.riskLevel : highest
  }, undefined)
}
