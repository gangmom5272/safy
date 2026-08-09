import apiClient from '../api/client'
import type { ApiResponse, PageResponse } from '../api/types'

export interface AdminLiveMessagePayload {
  reportId: number
  message: string
}

export interface ReportMessage {
  messageId: number
  reportId: number
  senderUserId: number
  senderRole: 'CITIZEN' | 'OPERATOR' | 'SUPER_ADMIN'
  content: string
  sentAt: string
}

export async function sendAdminLiveMessage({ reportId, message }: AdminLiveMessagePayload) {
  const response = await apiClient.post<ApiResponse<ReportMessage>>(
    `/operator/reports/${reportId}/messages`,
    { content: message },
  )
  return response.data.data
}

export async function getReportMessages(reportId: number) {
  const response = await apiClient.get<ApiResponse<PageResponse<ReportMessage>>>(
    `/operator/reports/${reportId}/messages`,
    { params: { page: 0, size: 50 } },
  )
  return response.data.data.content
}
