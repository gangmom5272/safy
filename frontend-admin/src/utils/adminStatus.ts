import type {
  AiAnalysisStatus,
  ReportStatus,
  ReviewStatus,
  RiskLevelValue,
} from '../types/admin'
import { normalizeRiskLevel } from './adminRisk'

export type StatusTone = 'neutral' | 'primary' | 'success' | 'warning' | 'danger'

const riskToneMap: Record<NonNullable<ReturnType<typeof normalizeRiskLevel>>, StatusTone> = {
  very_low: 'primary',
  low: 'success',
  medium: 'warning',
  high: 'warning',
  very_high: 'danger',
}

const reportToneMap: Record<ReportStatus, StatusTone> = {
  received: 'neutral',
  checking: 'primary',
  in_progress: 'warning',
  completed: 'success',
  rejected: 'danger',
  cancelled: 'neutral',
  reassigned: 'primary',
}

const reviewToneMap: Record<ReviewStatus, StatusTone> = {
  pending: 'neutral',
  approved: 'success',
  rejected: 'danger',
}

const aiToneMap: Record<AiAnalysisStatus, StatusTone> = {
  queued: 'neutral',
  analyzing: 'primary',
  detected: 'danger',
  completed: 'success',
}

export const getRiskTone = (status: RiskLevelValue): StatusTone => {
  const normalized = normalizeRiskLevel(status)
  return normalized ? riskToneMap[normalized] : 'neutral'
}
export const getReportStatusTone = (status: ReportStatus) => reportToneMap[status]
export const getReviewStatusTone = (status: ReviewStatus) => reviewToneMap[status]
export const getAiStatusTone = (status: AiAnalysisStatus) => aiToneMap[status]
