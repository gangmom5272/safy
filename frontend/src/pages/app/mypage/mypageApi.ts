import apiClient from '../../../api/client'
import type { Faq, FavoriteLocation, Inquiry, MyReport, Notice, ReportStatus, ReportSummary, UserProfile } from './mypageTypes'
const unwrap = <T,>(data: T | { data: T }) => typeof data === 'object' && data !== null && 'data' in data ? data.data : data
export async function getMe() { return unwrap((await apiClient.get<UserProfile | { data: UserProfile }>('/api/v1/users/me')).data) }
export async function updateMyProfile(nickname: string) {
  return unwrap((await apiClient.patch<UserProfile | { data: UserProfile }>('/api/v1/users/me', { nickname })).data)
}
export async function getReportSummary() { return unwrap((await apiClient.get<ReportSummary | { data: ReportSummary }>('/api/users/me/reports/summary')).data) }
export async function getMyReports(status?: ReportStatus) { const data = (await apiClient.get<MyReport[] | { content: MyReport[] }>('/api/users/me/reports', { params: { page: 0, size: 30, ...(status ? { status } : {}) } })).data; return Array.isArray(data) ? data : data.content }
export async function updateMyReport(id: number, report: Pick<MyReport, 'type' | 'description' | 'locationName'>) { return (await apiClient.patch<MyReport>(`/api/reports/${id}`, report)).data }
export async function deleteMyReport(id: number) { await apiClient.delete(`/api/reports/${id}`) }
export async function getFavorites() { return unwrap((await apiClient.get<FavoriteLocation[] | { data: FavoriteLocation[] }>('/api/users/me/favorite-locations')).data) }
export async function createFavorite(data: Omit<FavoriteLocation, 'id' | 'areaId' | 'riskLevel' | 'isPrimary'>) { return (await apiClient.post<FavoriteLocation>('/api/users/me/favorite-locations', data)).data }
export async function patchFavorite(id: number, data: Partial<FavoriteLocation>) { return (await apiClient.patch<FavoriteLocation>(`/api/users/me/favorite-locations/${id}`, data)).data }
export async function removeFavorite(id: number) { await apiClient.delete(`/api/users/me/favorite-locations/${id}`) }
export async function logout() { await apiClient.post('/api/auth/logout') }
export async function getFaqs(category?: string) { return unwrap((await apiClient.get<Faq[] | { data: Faq[] }>('/api/faqs', { params: category ? { category } : {} })).data) }
export async function getNotices() { const data = (await apiClient.get<Notice[] | { content: Notice[] }>('/api/notices', { params: { page: 0, size: 30 } })).data; return Array.isArray(data) ? data : data.content }
export async function getNotice(id: number) { return unwrap((await apiClient.get<Notice | { data: Notice }>(`/api/notices/${id}`)).data) }
export async function createInquiry(data: Pick<Inquiry, 'type' | 'title' | 'content' | 'attachmentName'>) { return (await apiClient.post<Inquiry>('/api/inquiries', data)).data }
export async function getInquiries() { const data = (await apiClient.get<Inquiry[] | { content: Inquiry[] }>('/api/users/me/inquiries', { params: { page: 0, size: 30 } })).data; return Array.isArray(data) ? data : data.content }
export async function getInquiry(id: number) { return unwrap((await apiClient.get<Inquiry | { data: Inquiry }>(`/api/users/me/inquiries/${id}`)).data) }
