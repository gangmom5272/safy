import type { MyReport, ReportAttachment, ReportCategoryCode, ReportStatus, StatusHistory } from './mypageTypes'

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

// 사용자에게 보여줄 상태 라벨. 목록 카드 배지·상태 필터 탭·상세 배지·처리 이력이 모두 이
// 표 하나만 쓴다. 같은 상태가 화면마다 다른 문구로 보이지 않게 하려는 것이다.
// 접수 대기 중(RECEIVED)은 담당자 확인 전이라 시민이 취소할 수 있는 구간이다.
const REPORT_STATUS_LABELS: Record<ReportStatus, string> = {
  RECEIVED: '접수 대기 중',
  IN_PROGRESS: '처리 중',
  CLOSED: '처리 완료',
  REJECTED: '반려됨',
  CANCELLED: '취소됨',
}

// 서버에 상태가 추가돼 모르는 값이 와도 배지가 빈칸으로 남지 않게 기본 문구를 준다.
export function getStatusLabel(status: ReportStatus) {
  return REPORT_STATUS_LABELS[status] ?? '상태 확인 중'
}

// 처리 이력 각 단계의 두 번째 줄. 서버 reason 에는 관리자 내부 코드가 들어 있어 그대로
// 쓸 수 없으므로, 사용자 관점의 문구를 상태별로 여기서 정해 둔다.
const REPORT_PROGRESS_DESCRIPTIONS: Record<ReportStatus, string> = {
  RECEIVED: '접수가 진행 중입니다.',
  IN_PROGRESS: '관리자가 처리 중입니다.',
  CLOSED: '관리자가 제보 처리를 완료했습니다.',
  REJECTED: '관리자 검토 결과 제보가 반려되었습니다.',
  CANCELLED: '요청에 따라 제보가 취소되었습니다.',
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
  // 화면에 그대로 쓰는 사용자용 안내 문구. 서버 원문이 아니다.
  description: string
  isCurrent: boolean
}

// 사용자에게 보여도 되는 사유는 최종 반려 이력의 reason 하나뿐이다. 다른 상태의 reason 은
// 관리자 내부 값이다. 백엔드가 쓰는 값을 확인한 결과는 다음과 같다.
//   RECEIVED    ← AUTO_OPERATOR_ASSIGNMENT, OPERATOR_OFFLINE 등 회수 사유,
//                 담당자 재배정 사유, 센터 이관 사유 (모두 관리자 업무용)
//   IN_PROGRESS ← OPERATOR_VIEWING, OPERATOR_CLAIMED
//   CLOSED      ← WORK_COMPLETED
//   CANCELLED   ← REPORTER_CANCELLED
//   REJECTED    ← 관리자가 입력한 반려 사유  ← 이것만 사용자에게 보여준다
// 그래서 상태로 걸러야 하고, 사유 문자열을 내용으로 판별하려 해서는 안 된다.
function findRejectReason(history: StatusHistory[]) {
  const reason = history
    .filter((item) => item.status === 'REJECTED')
    .at(-1)
    ?.reason
    ?.trim()

  return reason ? reason : null
}

// 반려 사유가 있으면 원문을 그대로 붙인다. 문장을 다듬거나 잘라내지 않는다.
function describeProgressStep(status: ReportStatus, rejectReason: string | null) {
  if (status === 'REJECTED' && rejectReason) {
    return `관리자가 다음 사유로 제보를 반려했습니다: ${rejectReason}`
  }

  return REPORT_PROGRESS_DESCRIPTIONS[status] ?? '처리 상태가 변경되었습니다.'
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
  const steps: Omit<ReportProgressStep, 'isCurrent' | 'description'>[] = [
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
  const rejectReason = findRejectReason(knownHistory)
  return steps.map((step, index) => ({
    ...step,
    description: describeProgressStep(step.status, rejectReason),
    isCurrent: index === steps.length - 1,
  }))
}

// 백엔드에는 사용자 표시용 접수 번호가 따로 없고 제보 PK(Long)만 있다. 그 값을 접수 번호로
// 쓰고, 쓸 수 없는 값이면 번호를 임의로 만들지 않고 확인 불가로 표시한다.
export function formatReceiptNumber(reportId: number | null | undefined) {
  if (typeof reportId !== 'number' || !Number.isSafeInteger(reportId) || reportId <= 0) {
    return '접수 번호 확인 불가'
  }

  return `접수 번호 #${reportId}`
}
