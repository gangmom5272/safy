import type { AdminReport, VerificationStatus } from '../types/admin'
import { toTimestamp } from './adminDate'

export interface VerificationConfig {
  label: string
  /** 위치 핀 전체 색. 확인 상태만 표현하며 위험도 색과 섞지 않는다. */
  pinColor: string
  badgeBackgroundColor: string
  badgeTextColor: string
}

export const VERIFICATION_CONFIG: Record<VerificationStatus, VerificationConfig> = {
  NEW: { label: '신규', pinColor: '#7C3AED', badgeBackgroundColor: '#F5F0FF', badgeTextColor: '#5B21B6' },
  CHECKED: { label: '확인', pinColor: '#004AAD', badgeBackgroundColor: '#EAF2FF', badgeTextColor: '#003B8A' },
}

/**
 * 신규로 강조하는 시간. 시민 지도의 최근 제보 창(PublicReportQueryService.RECENT_WINDOW)과
 * 같은 30분으로 맞춘다. 담당자가 다시 접속할 때 열어보지 않은 옛 배정까지 한꺼번에
 * 신규로 되살아나는 것을 막는 창이다.
 */
export const NEW_HIGHLIGHT_WINDOW_MINUTES = 30

const NEW_HIGHLIGHT_WINDOW_MS = NEW_HIGHLIGHT_WINDOW_MINUTES * 60 * 1000

/**
 * 아직 열어보지 않은 배정 상태.
 * 백엔드는 담당자가 상세를 조회하면 ASSIGNED → IN_PROGRESS 로 전이시키며 확인 시각을 남긴다.
 */
const UNCHECKED_ASSIGNMENT_STATUSES = new Set<NonNullable<AdminReport['assignmentStatus']>>([
  'ASSIGNED',
  'ACCEPTED',
])

export function verificationFromAssignmentStatus(
  status: AdminReport['assignmentStatus'],
): VerificationStatus {
  return status && UNCHECKED_ASSIGNMENT_STATUSES.has(status) ? 'NEW' : 'CHECKED'
}

/** 확인 여부를 알 수 없는 응답은 신규 강조를 남발하지 않도록 확인한 것으로 본다. */
export function resolveVerificationStatus(report: AdminReport): VerificationStatus {
  return report.verificationStatus ?? 'CHECKED'
}

/**
 * 담당자가 아직 열어보지 않은 제보. 시간이 지나도 변하지 않는 사실이므로
 * '미확인 제보' 집계는 강조 여부(isNewReport)가 아니라 이 값을 쓴다.
 */
export function isUncheckedReport(report: AdminReport): boolean {
  return resolveVerificationStatus(report) === 'NEW'
}

/**
 * 기준 시각을 받는 판정. 테스트에서 고정 시각을 넣기 위한 것이므로 화면 코드는
 * 인자 없는 {@link isNewReport} 를 쓴다. 여기에 기본값을 두면 filter(isNewReport) 처럼
 * 콜백으로 넘겼을 때 배열 인덱스가 기준 시각 자리에 들어간다.
 */
export function isNewReportAt(report: AdminReport, now: number): boolean {
  if (!isUncheckedReport(report)) return false

  const assignedAt = toTimestamp(report.assignedAt ?? report.reportedAt)
  return assignedAt !== null && now - assignedAt < NEW_HIGHLIGHT_WINDOW_MS
}

/**
 * 신규로 강조할 제보. 미확인이어도 배정된 지 30분이 지나면 강조를 내린다.
 * 강조만 내리는 것이라 제보는 지도와 목록에 그대로 남고 미확인 집계에도 계속 들어간다.
 * 기준 시각은 '내게 넘어온 시점'인 배정 시각이며, 재배정되면 그때부터 다시 30분이다.
 * 배정 시각이 없는 응답(담당 이력 등)은 제보 시각으로 대신한다.
 */
export function isNewReport(report: AdminReport): boolean {
  return isNewReportAt(report, Date.now())
}

/** 핀 색과 NEW 배지가 어긋나지 않도록 강조 기준(isNewReport)을 그대로 따른다. */
export function getVerificationConfig(report: AdminReport): VerificationConfig {
  return VERIFICATION_CONFIG[isNewReport(report) ? 'NEW' : 'CHECKED']
}
