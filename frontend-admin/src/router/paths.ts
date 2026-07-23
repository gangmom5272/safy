export const ROUTE_PATHS = {
  DASHBOARD: '/dashboard',
  REPORTS: '/reports',
  REPORT_DETAIL: '/reports/:reportId',
  MONITORING: '/monitoring',
  RISK_MAP: '/risk-map',
} as const

export const createReportDetailPath = (reportId: number | string) =>
  `/reports/${reportId}`
