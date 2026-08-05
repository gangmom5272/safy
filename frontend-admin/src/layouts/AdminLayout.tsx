import { useCallback, useEffect, useState } from 'react'
import { Outlet } from 'react-router'
import { startRealtimeEvents } from '../api/realtime'
import { AdminHeader } from '../components/layout/AdminHeader'
import { AdminSidebar } from '../components/layout/AdminSidebar'
import { useDashboardReports } from '../pages/dashboard/useDashboardReports'
import type { AdminReport, WebRTCConnection } from '../types/admin'
import { useAuth } from '../auth/useAuth'

export interface AdminLayoutOutletContext {
  markDashboardUpdated: () => void
  reports: AdminReport[]
  connections: WebRTCConnection[]
  isDashboardLoading: boolean
  dashboardLoadError: string
  markReportChecked: (reportId: number) => void
}

export function AdminLayout() {
  const { user } = useAuth()
  const [lastDashboardUpdatedAt, setLastDashboardUpdatedAt] = useState<number | null>(null)

  useEffect(() => startRealtimeEvents(), [])

  const markDashboardUpdated = useCallback(() => {
    setLastDashboardUpdatedAt(Date.now())
  }, [])
  const {
    reports,
    connections,
    isLoading: isDashboardLoading,
    loadError: dashboardLoadError,
    markChecked: markReportChecked,
  } = useDashboardReports(markDashboardUpdated, user?.role === 'OPERATOR')

  return (
    <div className="admin-shell">
      <AdminSidebar />
      <div className="admin-workspace">
        <AdminHeader
          lastDashboardUpdatedAt={lastDashboardUpdatedAt}
          reports={reports}
          onCheckReport={markReportChecked}
        />
        <main className="admin-main">
          <Outlet context={{
            markDashboardUpdated,
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
