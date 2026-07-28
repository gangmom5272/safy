import apiClient from '../../../api/client'
import { mockResponse, USE_MOCK_API } from '../../../api/mockMode'
import {
  mockFaqs,
  mockFavorites,
  mockInquiries,
  mockNotices,
  mockNotificationSettings,
  mockReports,
  mockSummary,
  mockUser,
} from './mypageMock'
import type { ApiResponse, PageResponse } from '../../../api/types'
import type { Faq, FavoriteLocation, Inquiry, MyReport, Notice, NotificationSettingsData, ReportStatus, ReportSummary, UserProfile } from './mypageTypes'

type BackendReportStatus = 'RECEIVED' | 'ASSIGNED' | 'ACCEPTED' | 'IN_PROGRESS' | 'CLOSED' | 'REJECTED' | 'CANCELLED'

interface ReportImageView {
  previewUrl: string | null
}

interface ReportListItemView {
  reportId: number
  reportType: 'IMAGE' | 'LIVE'
  category: string
  subcategory: string
  description: string
  address: string
  status: BackendReportStatus
  administrativeAreaName: string | null
  reportedAt: string
  representativeImage: ReportImageView | null
}

interface ReportDetailView extends ReportListItemView {
  images: ReportImageView[]
}

interface ReportStatusHistoryResponse {
  id: number
  toStatus: BackendReportStatus
  reason: string | null
  changedAt: string
}

interface MyReportSummaryResponse {
  totalCount: number
  activeCount: number
  closedCount: number
}

function toUiStatus(status: BackendReportStatus): ReportStatus {
  if (status === 'RECEIVED') return 'RECEIVED'
  if (status === 'ASSIGNED' || status === 'ACCEPTED' || status === 'IN_PROGRESS') return 'CHECKING'
  if (status === 'CLOSED') return 'COMPLETED'
  return status
}

function toMyReport(report: ReportListItemView, history: ReportStatusHistoryResponse[] = []): MyReport {
  const previewUrl = report.representativeImage?.previewUrl ?? null
  return {
    id: report.reportId,
    type: report.reportType === 'IMAGE' ? '이미지' : '라이브',
    title: report.description,
    description: report.description,
    locationName: report.administrativeAreaName ?? report.address ?? '위치 확인 중',
    address: report.address ?? '주소 정보 없음',
    createdAt: report.reportedAt,
    status: toUiStatus(report.status),
    thumbnailUrl: previewUrl,
    mediaUrls: previewUrl ? [previewUrl] : [],
    history: history.map((item) => ({
      status: toUiStatus(item.toStatus),
      changedAt: item.changedAt,
      description: item.reason ?? '제보 상태가 변경되었습니다.',
    })),
  }
}

export async function getMe() {
  if (USE_MOCK_API) return mockResponse(mockUser)

  const response = await apiClient.get<ApiResponse<{ id: number; displayName: string }>>('/users/me')
  return { id: response.data.data.id, nickname: response.data.data.displayName, email: '' } satisfies UserProfile
}

export async function updateMyProfile(nickname: string) {
  if (USE_MOCK_API) return mockResponse({ ...mockUser, nickname })

  const response = await apiClient.patch<ApiResponse<{ id: number; displayName: string }>>('/users/me', { displayName: nickname })
  return { id: response.data.data.id, nickname: response.data.data.displayName, email: '' } satisfies UserProfile
}

export async function getReportSummary(): Promise<ReportSummary> {
  if (USE_MOCK_API) return mockResponse(mockSummary)

  const response = await apiClient.get<ApiResponse<MyReportSummaryResponse>>('/reports/me/summary')
  const summary = response.data.data
  return { totalCount: summary.totalCount, receivedCount: summary.activeCount, checkingCount: 0, completedCount: summary.closedCount, rejectedCount: 0 }
}

export async function getMyReports() {
  if (USE_MOCK_API) return mockResponse(mockReports)

  const response = await apiClient.get<ApiResponse<PageResponse<ReportListItemView>>>('/reports/me', { params: { page: 0, size: 30 } })
  return response.data.data.content.map((report) => toMyReport(report))
}

export async function getMyReport(reportId: number) {
  if (USE_MOCK_API) return mockResponse(mockReports.find((report) => report.id === reportId) ?? mockReports[0])

  const [detailResponse, historyResponse] = await Promise.all([
    apiClient.get<ApiResponse<ReportDetailView>>(`/reports/${reportId}`),
    apiClient.get<ApiResponse<ReportStatusHistoryResponse[]>>(`/reports/${reportId}/status-history`),
  ])
  const detail = detailResponse.data.data
  const report = toMyReport({ ...detail, representativeImage: detail.images[0] ?? null }, historyResponse.data.data)
  report.mediaUrls = detail.images.flatMap((image) => image.previewUrl ? [image.previewUrl] : [])
  return report
}

export async function cancelMyReport(reportId: number) {
  if (USE_MOCK_API) return mockResponse(undefined)

  await apiClient.patch(`/reports/${reportId}/cancel`)
}

export async function logout() {
  if (USE_MOCK_API) return mockResponse(undefined)

  await apiClient.post('/auth/logout')
}

export async function getFavorites() { if (USE_MOCK_API) return mockResponse(mockFavorites); return (await apiClient.get<ApiResponse<FavoriteLocation[]>>('/users/me/favorite-locations')).data.data }
export async function createFavorite(data: Omit<FavoriteLocation, 'id' | 'areaId' | 'riskLevel' | 'isPrimary'>) { if (USE_MOCK_API) return mockResponse({ ...data, id: Date.now(), areaId: 0, riskLevel: 'SAFE', isPrimary: false } as FavoriteLocation); return (await apiClient.post<FavoriteLocation>('/users/me/favorite-locations', data)).data }
export async function patchFavorite(id: number, data: Partial<FavoriteLocation>) { if (USE_MOCK_API) return mockResponse({ ...(mockFavorites.find((favorite) => favorite.id === id) ?? mockFavorites[0]), ...data }); return (await apiClient.patch<FavoriteLocation>(`/users/me/favorite-locations/${id}`, data)).data }
export async function removeFavorite(id: number) { if (USE_MOCK_API) return mockResponse(undefined); await apiClient.delete(`/users/me/favorite-locations/${id}`) }
export async function getFaqs(category?: string) { if (USE_MOCK_API) return mockResponse(category ? mockFaqs.filter((faq) => faq.category === category) : mockFaqs); return (await apiClient.get<ApiResponse<Faq[]>>('/faqs', { params: category ? { category } : {} })).data.data }
export async function getNotices() { if (USE_MOCK_API) return mockResponse(mockNotices); return (await apiClient.get<ApiResponse<PageResponse<Notice>>>('/notices', { params: { page: 0, size: 30 } })).data.data.content }
export async function getNotice(id: number) { if (USE_MOCK_API) return mockResponse(mockNotices.find((notice) => notice.id === id) ?? mockNotices[0]); return (await apiClient.get<ApiResponse<Notice>>(`/notices/${id}`)).data.data }
export async function getInquiries() { if (USE_MOCK_API) return mockResponse(mockInquiries); return (await apiClient.get<ApiResponse<PageResponse<Inquiry>>>('/users/me/inquiries', { params: { page: 0, size: 30 } })).data.data.content }
export async function createInquiry(data: Pick<Inquiry, 'type' | 'title' | 'content' | 'attachmentName'>) { if (USE_MOCK_API) return mockResponse({ ...data, id: Date.now(), createdAt: new Date().toISOString(), status: 'PENDING', answer: null, answeredAt: null } as unknown as Inquiry); return (await apiClient.post<Inquiry>('/inquiries', data)).data }
export async function getInquiry(id: number) { if (USE_MOCK_API) return mockResponse(mockInquiries.find((inquiry) => inquiry.id === id) ?? mockInquiries[0]); return (await apiClient.get<ApiResponse<Inquiry>>(`/users/me/inquiries/${id}`)).data.data }
export async function getNotificationSettings() { if (USE_MOCK_API) return mockResponse(mockNotificationSettings); return (await apiClient.get<NotificationSettingsData>('/users/me/notification-settings')).data }
export async function updateNotificationSettings(settings: NotificationSettingsData) { if (USE_MOCK_API) return mockResponse(settings); return (await apiClient.patch<NotificationSettingsData>('/users/me/notification-settings', settings)).data }
