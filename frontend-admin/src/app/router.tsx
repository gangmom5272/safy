import { createBrowserRouter } from 'react-router'
import { ProtectedRoute } from '../auth/ProtectedRoute'
import { PublicOnlyRoute } from '../auth/PublicOnlyRoute'
import { RequireRole, RoleHome } from '../auth/RoleRoute'
import { AdminLayout } from '../layouts/AdminLayout'
import { DashboardPage } from '../pages/dashboard/DashboardPage'
import { LoginPage } from '../pages/login/LoginPage'
import { MonitoringPage } from '../pages/monitoring/MonitoringPage'
import { NotFoundPage } from '../pages/not-found/NotFoundPage'
import { OperatorManagementPage } from '../pages/operators/OperatorManagementPage'
import { QueuePage } from '../pages/queue/QueuePage'
import { ReportDetailPage } from '../pages/reports/ReportDetailPage'
import { ReportListPage } from '../pages/reports/ReportListPage'
import { RiskMapPage } from '../pages/risk-map/RiskMapPage'
import { SystemSettingsPage } from '../pages/settings/SystemSettingsPage'
import { AreaReportRequestPage } from '../pages/area-report-requests/AreaReportRequestPage'

// 관제(OPERATOR) 화면과 인사 관리(SUPER_ADMIN) 화면은 역할별로 완전히 분리한다.
const operatorOnly = (element: React.ReactNode) => (
  <RequireRole role="OPERATOR">{element}</RequireRole>
)

const superAdminOnly = (element: React.ReactNode) => (
  <RequireRole role="SUPER_ADMIN">{element}</RequireRole>
)

export const router = createBrowserRouter(
  [
    {
      path: '/',
      children: [
        {
          index: true,
          element: (
            <ProtectedRoute>
              <RoleHome />
            </ProtectedRoute>
          ),
        },
        {
          path: 'login',
          element: (
            <PublicOnlyRoute>
              <LoginPage />
            </PublicOnlyRoute>
          ),
        },
        {
          element: (
            <ProtectedRoute>
              <AdminLayout />
            </ProtectedRoute>
          ),
          children: [
            { path: 'dashboard', element: operatorOnly(<DashboardPage />) },
            { path: 'reports', element: operatorOnly(<ReportListPage />) },
            { path: 'reports/:reportId', element: operatorOnly(<ReportDetailPage />) },
            { path: 'queue', element: operatorOnly(<QueuePage />) },
            { path: 'monitoring', element: operatorOnly(<MonitoringPage />) },
            { path: 'monitoring/:connectionId', element: operatorOnly(<MonitoringPage />) },
            { path: 'risk-map', element: operatorOnly(<RiskMapPage />) },
            { path: 'area-report-requests', element: operatorOnly(<AreaReportRequestPage />) },
            { path: 'operators', element: superAdminOnly(<OperatorManagementPage />) },
            { path: 'settings', element: <SystemSettingsPage /> },
            { path: '*', element: <NotFoundPage /> },
          ],
        },
      ],
    },
  ],
  { basename: '/admin' },
)
