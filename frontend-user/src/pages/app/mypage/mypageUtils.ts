import type { FavoriteLocation, ReportAttachment } from './mypageTypes'

export const APP_VERSION = '1.0.0'

// 상세 응답의 ReportImageView 중 첨부 목록을 만들 때 쓰는 필드만 받는다. 목록 응답의
// representativeImage 도 같은 모양이라 그대로 넣을 수 있다.
type AttachmentSource = {
  previewUrl?: string | null
  contentType?: string | null
  originalFilename?: string | null
  displayOrder?: number | null
}

// presigned URL 은 쿼리가 붙어 오므로 확장자 뒤에 ? 가 올 수 있다.
const VIDEO_EXTENSION_PATTERN = /\.(mp4|webm|ogv|mov|m4v)(\?|#|$)/i

function toAttachment(source: AttachmentSource): ReportAttachment | null {
  const url = source.previewUrl?.trim()

  // 업로드가 아직 끝나지 않은 첨부는 previewUrl 이 null 로 온다. 표시할 게 없으므로 버린다.
  if (!url) return null

  return {
    url,
    contentType: source.contentType ?? null,
    filename: source.originalFilename ?? null,
  }
}

// 첨부는 배열이 정답이지만 들어오는 모양이 세 가지다. 상세 응답의 images 배열,
// 목록 응답의 representativeImage 단일 객체, 그리고 URL 문자열만 남은 예전 데이터.
// 어느 쪽이 와도 배열로 맞춰 화면이 한 가지 형태만 다루게 한다.
export function normalizeAttachments(
  source: AttachmentSource[] | AttachmentSource | string | null | undefined,
): ReportAttachment[] {
  if (!source) return []

  if (typeof source === 'string') {
    const url = source.trim()
    return url ? [{ url, contentType: null, filename: null }] : []
  }

  const sources = Array.isArray(source) ? source : [source]

  return sources
    // 서버가 정렬해서 주지만 순서를 보장받으려면 displayOrder 를 직접 쓴다.
    .slice()
    .sort((left, right) => (left.displayOrder ?? 0) - (right.displayOrder ?? 0))
    .map(toAttachment)
    .filter((attachment): attachment is ReportAttachment => attachment !== null)
}

// 사진과 영상 판단은 서버가 준 MIME 을 먼저 믿는다. 확장자는 MIME 이 없을 때만 본다.
export function isVideoAttachment(attachment: ReportAttachment) {
  if (attachment.contentType) {
    return attachment.contentType.toLowerCase().startsWith('video/')
  }

  return VIDEO_EXTENSION_PATTERN.test(attachment.url)
}

export function buildFavoriteSituationUrl(location: FavoriteLocation) {
  const params = new URLSearchParams({ query: location.placeName })
  return `/app/situations?${params.toString()}`
}
