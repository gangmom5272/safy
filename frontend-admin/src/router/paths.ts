export const ROUTE_PATHS = {
  LOGIN: '/login',
  DASHBOARD: '/dashboard',
  REPORTS: '/reports',
  REPORT_DETAIL: '/reports/:reportId',
  MONITORING: '/monitoring',
  RISK_MAP: '/risk-map',
  SETTINGS: '/settings',
} as const

export const createReportDetailPath = (reportId: number | string) =>
  `/reports/${reportId}`
