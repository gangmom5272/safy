import { useEffect } from 'react'
import { Outlet } from 'react-router'
import { startRealtimeEvents } from '../api/realtime'
import { AdminHeader } from '../components/layout/AdminHeader'
import { AdminSidebar } from '../components/layout/AdminSidebar'
import { useDashboardReports } from '../pages/dashboard/useDashboardReports'
import type { AdminReport, WebRTCConnection } from '../types/admin'
import { useAuth } from '../auth/useAuth'

export interface AdminLayoutOutletContext {
  reports: AdminReport[]
  connections: WebRTCConnection[]
  isDashboardLoading: boolean
  dashboardLoadError: string
  markReportChecked: (reportId: number) => void
}

export function AdminLayout() {
  const { user } = useAuth()

  useEffect(() => startRealtimeEvents(), [])

  const {
    reports,
    connections,
    isLoading: isDashboardLoading,
    loadError: dashboardLoadError,
    markChecked: markReportChecked,
  } = useDashboardReports(user?.role === 'OPERATOR')

  return (
    <div className="admin-shell">
      <AdminSidebar />
      <div className="admin-workspace">
        <AdminHeader
          reports={reports}
          onCheckReport={markReportChecked}
        />
        <main className="admin-main">
          <Outlet context={{
            reports,
            connections,
            isDashboardLoading,
            dashboardLoadError,
            markReportChecked,
          }} />
        </main>
      </div>
    </div>
  )
}
