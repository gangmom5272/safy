import type { MyReport, ReportAttachment, ReportCategoryCode, ReportStatus } from './mypageTypes'

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

// 목록 카드 썸네일로 쓸 URL. 서버는 원본을 주지 않고 블러 파생본만 주므로 마스킹된 값을
// 먼저 쓴다. 대표 이미지가 비어 있으면 첨부 중 사진 한 장을 마지막 수단으로 본다(영상
// 첨부는 <img> 로 그릴 수 없어 제외한다). 없으면 null 이고, 화면은 그때만 대체 표시를 쓴다.
export function pickReportThumbnailUrl(
  report: Pick<MyReport, 'thumbnailUrl' | 'maskedThumbnailUrl' | 'attachments'>,
) {
  const direct = report.maskedThumbnailUrl?.trim() || report.thumbnailUrl?.trim()
  if (direct) return direct

  return report.attachments.find((attachment) => !isVideoAttachment(attachment))?.url ?? null
}

// 사용자에게 보여줄 처리 단계 순서다. 종결 상태 세 개는 서로 배타적이라 같은 자리를 쓴다.
const REPORT_PROGRESS_ORDER: Record<ReportStatus, number> = {
  RECEIVED: 0,
  IN_PROGRESS: 1,
  CLOSED: 2,
  REJECTED: 2,
  CANCELLED: 2,
}

export interface ReportProgressStep {
  status: ReportStatus
  // 그 단계에 처음 진입한 시각(종결 단계는 마지막 시각)이다. 이력에 없으면 시각을
  // 만들어내지 않고 null 로 두고, 화면은 시각을 생략한다.
  changedAt: string | null
  isCurrent: boolean
}

// 원본 처리 이력에는 담당자 자동 배정·오프라인·재배정·센터 이관 같은 관리자 업무 이력이
// 모두 들어 있고 reason 에 내부 코드(AUTO_OPERATOR_ASSIGNMENT 등)가 그대로 담긴다.
// 관리자 화면은 그 원본이 필요하므로 history 는 건드리지 않고, 사용자 화면에서 쓸
// 접수 → 처리중 → 종결 세 단계만 여기서 새로 계산한다. 같은 단계에 해당하는 관리자
// 이력이 몇 건이든 단계는 한 번만 나오고, 현재 단계보다 뒤선 단계는 내보내지 않는다.
export function toReportProgressSteps(
  report: Pick<MyReport, 'status' | 'createdAt' | 'history'>,
): ReportProgressStep[] {
  const knownHistory = report.history.filter((item) => {
    if (item.status in REPORT_PROGRESS_ORDER) return true
    // 서버에 상태가 추가돼도 내부 코드를 화면에 내보내지 않는다. 대신 개발 중에는
    // 로그로 남긴다. env 는 옵셔널로 읽는다. 이 파일은 node --test 로도 돌아가고
    // 그때는 Vite 가 import.meta.env 를 넣어주지 않는다.
    if (import.meta.env?.DEV) console.warn('알 수 없는 제보 상태를 처리 이력에서 건너뜁니다.', item.status)
    return false
  })
  const currentOrder = REPORT_PROGRESS_ORDER[report.status] ?? 0
  const steps: Omit<ReportProgressStep, 'isCurrent'>[] = [
    // 접수는 늘 도달한 단계다. 시각도 이력이 아니라 제보 접수 시각을 쓴다.
    { status: 'RECEIVED', changedAt: report.createdAt },
  ]

  // 처리중은 실제로 거쳐간 흔적이 있을 때만 넣는다. 관리자가 접수 직후 종결했다면
  // 도달하지 않은 단계이므로 보여주지 않는다.
  if (currentOrder >= 1 && (report.status === 'IN_PROGRESS' || knownHistory.some((item) => item.status === 'IN_PROGRESS'))) {
    steps.push({
      status: 'IN_PROGRESS',
      changedAt: knownHistory.find((item) => item.status === 'IN_PROGRESS')?.changedAt ?? null,
    })
  }

  // 종결 단계는 현재 상태 그대로 쓴다. 반려·취소를 처리 완료로 바꾸지 않는다.
  if (currentOrder === 2) {
    steps.push({
      status: report.status,
      changedAt: knownHistory.filter((item) => item.status === report.status).at(-1)?.changedAt ?? null,
    })
  }

  // 현재 단계보다 뒤선 단계는 넣지 않으므로 마지막 단계가 곧 현재 단계다.
  return steps.map((step, index) => ({ ...step, isCurrent: index === steps.length - 1 }))
}

// 백엔드에는 사용자 표시용 접수 번호가 따로 없고 제보 PK(Long)만 있다. 그 값을 접수 번호로
// 쓰고, 쓸 수 없는 값이면 번호를 임의로 만들지 않고 확인 불가로 표시한다.
export function formatReceiptNumber(reportId: number | null | undefined) {
  if (typeof reportId !== 'number' || !Number.isSafeInteger(reportId) || reportId <= 0) {
    return '접수 번호 확인 불가'
  }

  return `접수 번호 #${reportId}`
}
