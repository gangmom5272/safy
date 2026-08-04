import apiClient from '../../../api/client'
import { mockResponse, USE_MOCK_API } from '../../../api/mockMode'
import {
  mockFaqs,
  mockInquiries,
  mockNotices,
  mockNotificationSettings,
  mockReports,
  mockSummary,
  mockUser,
} from './mypageMock'
import type { ApiResponse, PageResponse } from '../../../api/types'
import type { Faq, Inquiry, MyReport, Notice, NotificationSettingsData, ReportCategoryCode, ReportStatus, ReportSummary, ReportTypeCode, UserProfile } from './mypageTypes'
import { normalizeAttachments } from './mypageUtils'

type BackendReportStatus = 'RECEIVED' | 'ASSIGNED' | 'ACCEPTED' | 'IN_PROGRESS' | 'CLOSED' | 'REJECTED' | 'CANCELLED'

// docs/api-contract.md 의 ReportImageView. 사진·영상 구분에 contentType 이 필요하고,
// 표시 순서를 서버 기준으로 맞추려면 displayOrder 도 읽어야 한다.
interface ReportImageView {
  previewUrl: string | null
  contentType: string | null
  originalFilename: string | null
  displayOrder: number | null
}

interface ReportListItemView {
  reportId: number
  reportType: ReportTypeCode
  // 서버는 enum 이름(CROWD_CONGESTION 등)을 그대로 준다. 한글 라벨 변환은 화면에서 한다.
  category: ReportCategoryCode
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

// 시민 취소(PATCH /reports/{id}/cancel)는 백엔드에서 RECEIVED·ASSIGNED 에서만 허용된다.
// ASSIGNED 는 서버가 관제센터로 자동 배정만 한 상태이고 담당자는 아직 확인하지 않았으므로
// 접수 대기(RECEIVED)로 묶는다. 담당자가 확인해 IN_PROGRESS 로 넘어가면 취소할 수 없으므로
// 여기서 가른다. 이렇게 두면 UI 상태가 취소 가능 구간과 그대로 일치한다.
// ACCEPTED 는 수동 수락 단계가 폐지되며 남은 레거시 값이라 IN_PROGRESS 와 같게 취급한다.
function toUiStatus(status: BackendReportStatus): ReportStatus {
  if (status === 'RECEIVED' || status === 'ASSIGNED') return 'RECEIVED'
  if (status === 'ACCEPTED') return 'IN_PROGRESS'
  return status
}

// 목록 응답에는 대표 이미지 하나만 온다. 상세는 images 배열을 주므로 getMyReport 가
// attachments 를 덮어쓴다.
function toMyReport(report: ReportListItemView, history: ReportStatusHistoryResponse[] = []): MyReport {
  const previewUrl = report.representativeImage?.previewUrl ?? null
  return {
    id: report.reportId,
    type: report.reportType === 'IMAGE' ? '이미지' : '라이브',
    reportType: report.reportType,
    category: report.category,
    title: report.description,
    description: report.description,
    locationName: report.administrativeAreaName ?? report.address ?? '위치 확인 중',
    address: report.address ?? '주소 정보 없음',
    createdAt: report.reportedAt,
    status: toUiStatus(report.status),
    thumbnailUrl: previewUrl,
    attachments: normalizeAttachments(report.representativeImage),
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
  // 서버는 totalCount·activeCount·closedCount 만 준다. 진행중/반려 세부 건수는 알 수 없어
  // 0 으로 둔다. 제보 목록을 이미 받아온 화면은 목록에서 직접 세는 쪽을 쓴다.
  return { totalCount: summary.totalCount, receivedCount: summary.activeCount, inProgressCount: 0, closedCount: summary.closedCount, rejectedCount: 0 }
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
  // 상세 응답은 첨부 전체를 images 배열로 준다. 대표 이미지 하나로 만든 값을 덮어쓴다.
  report.attachments = normalizeAttachments(detail.images)
  return report
}

export async function cancelMyReport(reportId: number) {
  if (USE_MOCK_API) {
    // 실제 서버는 취소(204) 후 상세 조회에서 CANCELLED 를 준다. 목도 같은 흐름이 되도록
    // 상태를 바꿔 둔다. 이러지 않으면 목 모드에서만 취소 후 다시 접수 대기 중으로 보인다.
    const index = mockReports.findIndex((report) => report.id === reportId)
    if (index >= 0) mockReports[index] = { ...mockReports[index], status: 'CANCELLED' }
    return mockResponse(undefined)
  }

  await apiClient.patch(`/reports/${reportId}/cancel`)
}

export async function logout() {
  if (USE_MOCK_API) return mockResponse(undefined)

  await apiClient.post('/auth/logout')
}

export async function getFaqs(category?: string) { if (USE_MOCK_API) return mockResponse(category ? mockFaqs.filter((faq) => faq.category === category) : mockFaqs); return (await apiClient.get<ApiResponse<Faq[]>>('/faqs', { params: category ? { category } : {} })).data.data }
export async function getNotices() { if (USE_MOCK_API) return mockResponse(mockNotices); return (await apiClient.get<ApiResponse<PageResponse<Notice>>>('/notices', { params: { page: 0, size: 30 } })).data.data.content }
export async function getNotice(id: number) { if (USE_MOCK_API) return mockResponse(mockNotices.find((notice) => notice.id === id) ?? mockNotices[0]); return (await apiClient.get<ApiResponse<Notice>>(`/notices/${id}`)).data.data }
export async function getInquiries() { if (USE_MOCK_API) return mockResponse(mockInquiries); return (await apiClient.get<ApiResponse<PageResponse<Inquiry>>>('/users/me/inquiries', { params: { page: 0, size: 30 } })).data.data.content }
export async function createInquiry(data: Pick<Inquiry, 'type' | 'title' | 'content' | 'attachmentName'>) { if (USE_MOCK_API) return mockResponse({ ...data, id: Date.now(), createdAt: new Date().toISOString(), status: 'PENDING', answer: null, answeredAt: null } as unknown as Inquiry); return (await apiClient.post<Inquiry>('/inquiries', data)).data }
export async function getInquiry(id: number) { if (USE_MOCK_API) return mockResponse(mockInquiries.find((inquiry) => inquiry.id === id) ?? mockInquiries[0]); return (await apiClient.get<ApiResponse<Inquiry>>(`/users/me/inquiries/${id}`)).data.data }
export async function getNotificationSettings() { if (USE_MOCK_API) return mockResponse(mockNotificationSettings); return (await apiClient.get<NotificationSettingsData>('/users/me/notification-settings')).data }
export async function updateNotificationSettings(settings: NotificationSettingsData) { if (USE_MOCK_API) return mockResponse(settings); return (await apiClient.patch<NotificationSettingsData>('/users/me/notification-settings', settings)).data }
