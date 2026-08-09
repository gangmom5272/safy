import { useCallback, useEffect, useState } from 'react'
import { Outlet } from 'react-router'
import { startRealtimeEvents } from '../api/realtime'
import { AdminHeader } from '../components/layout/AdminHeader'
import { AdminSidebar, type SidebarBadgeCounts } from '../components/layout/AdminSidebar'
import { useDashboardReports } from '../pages/dashboard/useDashboardReports'
import { ROUTE_PATHS } from '../router/paths'
import { isNewReport } from '../utils/adminVerification'
import type { AdminReport, WebRTCConnection } from '../types/admin'
import { useAuth } from '../auth/useAuth'

export interface AdminLayoutOutletContext {
  reports: AdminReport[]
  connections: WebRTCConnection[]
  isDashboardLoading: boolean
  dashboardLoadError: string
  markReportChecked: (reportId: number) => void
}

const sidebarCollapsedKey = 'safy-admin-sidebar-collapsed'

function readSidebarCollapsed(): boolean {
  try {
    return window.localStorage.getItem(sidebarCollapsedKey) === 'true'
  } catch {
    return false
  }
}

export function AdminLayout() {
  const { user } = useAuth()
  const [isSidebarCollapsed, setIsSidebarCollapsed] = useState(readSidebarCollapsed)

  useEffect(() => startRealtimeEvents(), [])

  useEffect(() => {
    window.localStorage.setItem(sidebarCollapsedKey, String(isSidebarCollapsed))
  }, [isSidebarCollapsed])

  const toggleSidebar = useCallback(() => setIsSidebarCollapsed((collapsed) => !collapsed), [])

  const {
    reports,
    connections,
    isLoading: isDashboardLoading,
    loadError: dashboardLoadError,
    markChecked: markReportChecked,
  } = useDashboardReports(user?.role === 'OPERATOR')

  // 좌측 탭 배지는 헤더·홈이 이미 쓰는 같은 목록에서 계산한다. 별도 API 나 목업을 두지 않는다.
  const sidebarBadgeCounts: SidebarBadgeCounts = {
    [ROUTE_PATHS.REPORTS]: reports.filter(isNewReport).length,
    [ROUTE_PATHS.MONITORING]: connections.filter(
      (connection) => connection.connectionStatus !== 'ended',
    ).length,
  }

  return (
    <div className={isSidebarCollapsed ? 'admin-shell admin-shell--collapsed' : 'admin-shell'}>
      <AdminSidebar
        collapsed={isSidebarCollapsed}
        onToggleCollapsed={toggleSidebar}
        badgeCounts={sidebarBadgeCounts}
      />
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
