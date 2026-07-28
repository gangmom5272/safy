import apiClient from '../../../api/client'
import { mockResponse, USE_MOCK_API } from '../../../api/mockMode'
import type { ApiResponse } from '../../../api/types'
import { uploadReportImage } from './mediaUploadFlow'
import type { IssueImageUploadRequest, IssueImageUploadResponse } from './mediaUploadFlow'

async function issueImageUpload(reportId: number, request: IssueImageUploadRequest) {
  const response = await apiClient.post<ApiResponse<IssueImageUploadResponse>>(
    `/reports/${reportId}/images/presigned-upload`,
    request,
  )
  return response.data.data
}

async function putImageDirectly(url: string, file: File, headers: Record<string, string>) {
  const response = await fetch(url, {
    method: 'PUT',
    headers,
    body: file,
  })
  if (!response.ok) {
    throw new Error(`이미지 업로드에 실패했습니다. (${response.status})`)
  }
}

async function completeImageUpload(reportId: number, mediaAssetId: number) {
  await apiClient.post(`/reports/${reportId}/images/${mediaAssetId}/complete`)
}

export function uploadImage(reportId: number, file: File) {
  // 목 모드에서는 MinIO 가 없으므로 업로드 성공만 흉내낸다.
  if (USE_MOCK_API) return mockResponse(undefined, 400)

  return uploadReportImage(reportId, file, {
    issue: issueImageUpload,
    put: putImageDirectly,
    complete: completeImageUpload,
  })
}
