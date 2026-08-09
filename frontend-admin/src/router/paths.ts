export const ROUTE_PATHS = {
  LOGIN: '/login',
  DASHBOARD: '/dashboard',
  REPORTS: '/reports',
  REPORT_DETAIL: '/reports/:reportId',
  QUEUE: '/queue',
  MONITORING: '/monitoring',
  MONITORING_DETAIL: '/monitoring/:connectionId',
  RISK_MAP: '/risk-map',
  AREA_REPORT_REQUESTS: '/area-report-requests',
  SETTINGS: '/settings',
  OPERATORS: '/operators',
} as const

export const createReportDetailPath = (reportId: number | string) =>
  `/reports/${reportId}`

export const createMonitoringDetailPath = (connectionId: number | string) =>
  `/monitoring/${encodeURIComponent(String(connectionId))}`
