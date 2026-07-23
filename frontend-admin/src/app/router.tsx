import { Navigate, createBrowserRouter } from 'react-router'
import { AdminLayout } from '../layouts/AdminLayout'
import { DashboardPage } from '../pages/dashboard/DashboardPage'
import { MonitoringPage } from '../pages/monitoring/MonitoringPage'
import { NotFoundPage } from '../pages/not-found/NotFoundPage'
import { ReportDetailPage } from '../pages/reports/ReportDetailPage'
import { ReportListPage } from '../pages/reports/ReportListPage'
import { RiskMapPage } from '../pages/risk-map/RiskMapPage'
import { ROUTE_PATHS } from '../router/paths'

export const router = createBrowserRouter(
  [
    {
      path: '/',
      element: <AdminLayout />,
      children: [
        { index: true, element: <Navigate to={ROUTE_PATHS.DASHBOARD} replace /> },
        { path: 'dashboard', element: <DashboardPage /> },
        { path: 'reports', element: <ReportListPage /> },
        { path: 'reports/:reportId', element: <ReportDetailPage /> },
        { path: 'monitoring', element: <MonitoringPage /> },
        { path: 'risk-map', element: <RiskMapPage /> },
        { path: '*', element: <NotFoundPage /> },
      ],
    },
  ],
  { basename: '/admin' },
)
