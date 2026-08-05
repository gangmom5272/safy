import type { AdminReport } from '../../types/admin'

function assignmentTime(report: AdminReport) {
  const parsed = Date.parse(report.assignedAt ?? report.reportedAt)
  return Number.isNaN(parsed) ? Number.NEGATIVE_INFINITY : parsed
}

/**
 * 배정 이력 API는 재배정될 때마다 같은 제보 ID를 여러 행으로 반환한다.
 * 목록에서는 제보 자체를 세어야 하므로 가장 최근 배정 상태 한 건만 남긴다.
 */
export function deduplicateReportsById(reports: AdminReport[]) {
  const latestByReportId = new Map<number, AdminReport>()

  for (const report of reports) {
    const current = latestByReportId.get(report.id)
    if (!current) {
      latestByReportId.set(report.id, report)
      continue
    }

    const reportTime = assignmentTime(report)
    const currentTime = assignmentTime(current)
    const isNewer = reportTime > currentTime
      || (reportTime === currentTime
        && (report.assignmentId ?? 0) > (current.assignmentId ?? 0))

    if (isNewer) latestByReportId.set(report.id, report)
  }

  return [...latestByReportId.values()]
}
