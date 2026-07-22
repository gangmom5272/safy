import { NavLink, Outlet } from 'react-router'

export function AdminLayout() {
  return (
    <div className="admin-shell">
      <aside className="admin-sidebar">
        <strong>SAFE:ON Admin</strong>
        <NavLink to="/" end>
          대시보드
        </NavLink>
      </aside>
      <main className="admin-main">
        <Outlet />
      </main>
    </div>
  )
}
