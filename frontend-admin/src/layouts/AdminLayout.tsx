import { useCallback, useEffect, useState } from 'react'
import { Outlet } from 'react-router'
import { startRealtimeEvents } from '../api/realtime'
import { AdminHeader } from '../components/layout/AdminHeader'
import { AdminSidebar } from '../components/layout/AdminSidebar'

export interface AdminLayoutOutletContext {
  markDashboardUpdated: () => void
}

export function AdminLayout() {
  const [lastDashboardUpdatedAt, setLastDashboardUpdatedAt] = useState<number | null>(null)

  useEffect(() => startRealtimeEvents(), [])

  const markDashboardUpdated = useCallback(() => {
    setLastDashboardUpdatedAt(Date.now())
  }, [])

  return (
    <div className="admin-shell">
      <AdminSidebar />
      <div className="admin-workspace">
        <AdminHeader lastDashboardUpdatedAt={lastDashboardUpdatedAt} />
        <main className="admin-main">
          <Outlet context={{ markDashboardUpdated }} />
        </main>
      </div>
    </div>
  )
}
