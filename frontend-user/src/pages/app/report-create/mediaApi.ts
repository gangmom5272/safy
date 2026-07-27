import apiClient from '../../../api/client'
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
  return uploadReportImage(reportId, file, {
    issue: issueImageUpload,
    put: putImageDirectly,
    complete: completeImageUpload,
  })
}
