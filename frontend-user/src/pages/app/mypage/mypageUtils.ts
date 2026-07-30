import type { FavoriteLocation, MyReport, ReportAttachment, ReportCategoryCode } from './mypageTypes'

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

// 백엔드 ReportCategory enum → 화면 라벨. 서버는 enum 이름만 주고 한글 라벨은 프론트에만
// 있다(제보 등록 화면의 REPORT_CATEGORY_LABELS 와 같은 표를 마이페이지에서도 쓴다).
const REPORT_CATEGORY_LABELS: Record<ReportCategoryCode, string> = {
  CROWD_CONGESTION: '인파 밀집',
  ACCIDENT_RISK: '사고·위험',
  FLOOD_WEATHER: '침수·기상',
  FACILITY_ROAD: '시설물·도로',
  OTHER: '기타',
}

// 서버에 카테고리가 추가돼 모르는 값이 와도 화면이 비지 않게 기본 라벨을 준다.
export function getReportCategoryLabel(category: ReportCategoryCode | null | undefined) {
  if (!category) return '카테고리 없음'

  return REPORT_CATEGORY_LABELS[category] ?? '기타'
}

// 유형은 실제 첨부를 먼저 본다. 사진이 여러 장이어도 이미지 하나로 표시하고, 첨부가 비어
// 있는 라이브 제보는 원본 유형(reportType)으로 판단한다.
export function getReportMediaTypeLabel(report: Pick<MyReport, 'reportType' | 'attachments'>) {
  if (report.attachments.some(isVideoAttachment)) return '영상'
  if (report.attachments.length) return '이미지'

  return report.reportType === 'LIVE' ? '영상' : '이미지'
}

// 백엔드에는 사용자 표시용 접수 번호가 따로 없고 제보 PK(Long)만 있다. 그 값을 접수 번호로
// 쓰고, 쓸 수 없는 값이면 번호를 임의로 만들지 않고 확인 불가로 표시한다.
export function formatReceiptNumber(reportId: number | null | undefined) {
  if (typeof reportId !== 'number' || !Number.isSafeInteger(reportId) || reportId <= 0) {
    return '접수 번호 확인 불가'
  }

  return `접수 번호 #${reportId}`
}

export function buildFavoriteSituationUrl(location: FavoriteLocation) {
  const params = new URLSearchParams({ query: location.placeName })
  return `/app/situations?${params.toString()}`
}
