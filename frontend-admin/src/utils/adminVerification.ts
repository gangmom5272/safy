import type { AdminReport, VerificationStatus } from '../types/admin'

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

export function isNewReport(report: AdminReport): boolean {
  return resolveVerificationStatus(report) === 'NEW'
}

export function getVerificationConfig(report: AdminReport): VerificationConfig {
  return VERIFICATION_CONFIG[resolveVerificationStatus(report)]
}
