import { createBrowserRouter, Navigate } from 'react-router'
import { AdminLayout } from '../layouts/AdminLayout'
import { AppLayout } from '../layouts/AppLayout'
import { AdminDashboardPage } from '../pages/admin/AdminDashboardPage'
import { NotFoundPage } from '../pages/common/NotFoundPage'
import { GuidePage } from '../pages/app/guide/GuidePage'
import { PrivacyPage } from '../pages/app/guide/PrivacyPage'
import { TermsPage } from '../pages/app/guide/TermsPage'
import { HomePage } from '../pages/app/home/HomePage'
import { FavoritesPage } from '../pages/app/mypage/FavoritesPage'
import { MyPage } from '../pages/app/mypage/MyPage'
import { MyReportDetailPage } from '../pages/app/mypage/MyReportDetailPage'
import { MyReportsPage } from '../pages/app/mypage/MyReportsPage'
import { NotificationsPage } from '../pages/app/notifications/NotificationsPage'
import { ReportCreatePage } from '../pages/app/report-create/ReportCreatePage'
import { ReportCompletePage } from '../pages/app/report-create/ReportCompletePage'
import { ReportTypePage } from '../pages/app/report-create/ReportTypePage'
import { LiveReportPage } from '../pages/app/report-create/LiveReportPage'
import { MediaReportPage } from '../pages/app/report-create/MediaReportPage'
import { ReportDetailPage } from '../pages/app/reports/ReportDetailPage'
import { AreaSituationPage } from '../pages/app/situations/AreaSituationPage'
import { SituationsPage } from '../pages/app/situations/SituationsPage'
import { InquiryDetailPage } from '../pages/app/support/InquiryDetailPage'
import { NoticeDetailPage } from '../pages/app/support/NoticeDetailPage'
import { SupportPage } from '../pages/app/support/SupportPage'

export const router = createBrowserRouter([
  {
    path: '/',
    element: <Navigate to="/app" replace />,
  },
  {
    path: '/app',
    element: <AppLayout />,
    children: [
      { index: true, element: <HomePage /> },
      { path: 'situations', element: <SituationsPage /> },
      { path: 'situations/areas/:areaId', element: <AreaSituationPage /> },
      { path: 'reports/:reportId', element: <ReportDetailPage /> },
      { path: 'report', element: <ReportCreatePage /> },
      { path: 'report/type', element: <ReportTypePage /> },
      { path: 'report/live', element: <LiveReportPage /> },
      { path: 'report/media', element: <MediaReportPage /> },
      { path: 'report/complete', element: <ReportCompletePage /> },
      { path: 'notifications', element: <NotificationsPage /> },
      { path: 'mypage', element: <MyPage /> },
      { path: 'mypage/reports', element: <MyReportsPage /> },
      { path: 'mypage/reports/:reportId', element: <MyReportDetailPage /> },
      { path: 'mypage/favorites', element: <FavoritesPage /> },
      { path: 'mypage/support', element: <SupportPage /> },
      { path: 'mypage/support/notices/:noticeId', element: <NoticeDetailPage /> },
      { path: 'mypage/support/inquiries/:inquiryId', element: <InquiryDetailPage /> },
      { path: 'mypage/guide', element: <GuidePage /> },
      { path: 'terms', element: <TermsPage /> },
      { path: 'privacy', element: <PrivacyPage /> },
    ],
  },
  {
    path: '/admin',
    element: <AdminLayout />,
    children: [{ index: true, element: <AdminDashboardPage /> }],
  },
  {
    path: '*',
    element: <NotFoundPage />,
  },
])
