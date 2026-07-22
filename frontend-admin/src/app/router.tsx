import { createBrowserRouter } from 'react-router'
import { AdminLayout } from '../layouts/AdminLayout'
import { AdminDashboardPage } from '../pages/admin/AdminDashboardPage'

export const router = createBrowserRouter(
  [
    {
      path: '/',
      element: <AdminLayout />,
      children: [{ index: true, element: <AdminDashboardPage /> }],
    },
  ],
  // 경로 기반 서빙(/admin/) 대응. 서브도메인 전환 시 basename 제거.
  { basename: '/admin' },
)
