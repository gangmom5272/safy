export interface IssueImageUploadRequest {
  filename: string
  contentType: string
}

export interface IssueImageUploadResponse {
  mediaAssetId: number
  uploadUrl: string
  uploadMethod: 'PUT'
  requiredHeaders: Record<string, string>
  maxFileSize: number
  expiresAt: string
}

interface ImageUploadDependencies {
  issue: (reportId: number, request: IssueImageUploadRequest) => Promise<IssueImageUploadResponse>
  put: (url: string, body: File, headers: Record<string, string>) => Promise<void>
  complete: (reportId: number, mediaAssetId: number) => Promise<void>
}

export async function uploadReportImage(
  reportId: number,
  file: File,
  dependencies: ImageUploadDependencies,
) {
  const issued = await dependencies.issue(reportId, {
    filename: file.name,
    contentType: file.type,
  })

  if (file.size > issued.maxFileSize) {
    throw new Error(`이미지는 ${issued.maxFileSize}바이트 이하만 업로드할 수 있습니다.`)
  }

  await dependencies.put(issued.uploadUrl, file, issued.requiredHeaders)
  await dependencies.complete(reportId, issued.mediaAssetId)
}
