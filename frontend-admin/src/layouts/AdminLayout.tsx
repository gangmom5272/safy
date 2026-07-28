import { useEffect } from 'react'
import { Outlet } from 'react-router'
import { startRealtimeEvents } from '../api/realtime'
import { AdminHeader } from '../components/layout/AdminHeader'
import { AdminSidebar } from '../components/layout/AdminSidebar'

export function AdminLayout() {
  useEffect(() => startRealtimeEvents(), [])

  return (
    <div className="admin-shell">
      <AdminSidebar />
      <div className="admin-workspace">
        <AdminHeader />
        <main className="admin-main">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
