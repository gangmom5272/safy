import type { AdminReport } from '../../types/admin'

export function getReportRowKey(report: Pick<AdminReport, 'id' | 'assignmentId'>) {
  return report.assignmentId ?? report.id
}
