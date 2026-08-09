import apiClient from '../../../api/client'
import { mockResponse, USE_MOCK_API } from '../../../api/mockMode'
import type { ApiResponse, PageResponse } from '../../../api/types'

export interface ReportMessage {
  messageId: number
  reportId: number
  senderUserId: number
  senderRole: 'CITIZEN' | 'OPERATOR' | 'SUPER_ADMIN'
  content: string
  sentAt: string
}

export async function getCitizenReportMessages(reportId: number) {
  if (USE_MOCK_API) return mockResponse<ReportMessage[]>([])

  const response = await apiClient.get<ApiResponse<PageResponse<ReportMessage>>>(
    `/reports/${reportId}/messages`,
    { params: { page: 0, size: 50 } },
  )
  return response.data.data.content
}
