import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { AdminModal } from '../../components/admin/AdminModal'
import { AdminSection } from '../../components/admin/AdminSection'
import { RiskLevelIndicator } from '../../components/admin/RiskLevelIndicator'
import { StatusBadge } from '../../components/admin/StatusBadge'
import { RiskBadge } from '../../components/admin/RiskBadge'
import {
  adminReports,
  aiAnalysisStatusLabels,
  reportStatusLabels,
  reportTypeLabels,
  reviewStatusLabels,
} from '../../mocks/adminMockData'
import { ROUTE_PATHS } from '../../router/paths'
import type { ReportStatus, ReviewStatus } from '../../types/admin'
import { formatAdminDate } from '../../utils/adminDate'
import {
  getAiStatusTone,
  getReportStatusTone,
  getReviewStatusTone,
} from '../../utils/adminStatus'

type StatusDialogState =
  | { type: 'review'; nextReviewStatus: Extract<ReviewStatus, 'approved' | 'rejected'> }
  | { type: 'processing'; nextStatus: ReportStatus }
  | null

type TimelineState = 'complete' | 'current' | 'pending' | 'failed'

const nextReportStatusMap: Partial<Record<ReportStatus, ReportStatus>> = {
  received: 'checking',
  checking: 'in_progress',
  in_progress: 'completed',
}

function canChangeReviewStatus(currentStatus: ReviewStatus, nextStatus: ReviewStatus): nextStatus is Extract<ReviewStatus, 'approved' | 'rejected'> {
  return currentStatus === 'pending' && (nextStatus === 'approved' || nextStatus === 'rejected')
}

function canChangeReportStatus(reviewStatus: ReviewStatus, currentStatus: ReportStatus, nextStatus: ReportStatus) {
  return reviewStatus === 'approved' && nextReportStatusMap[currentStatus] === nextStatus
}

interface TimelineStepProps {
  title: string
  state: TimelineState
  children: React.ReactNode
}

function TimelineStep({ title, state, children }: TimelineStepProps) {
  return (
    <li className={`status-timeline__step status-timeline__step--${state}`}>
      <span className="status-timeline__marker" aria-hidden="true" />
      <div className="status-timeline__content"><h3>{title}</h3>{children}</div>
    </li>
  )
}

export function ReportDetailPage() {
  const { reportId } = useParams()
  const report = adminReports.find((item) => String(item.id) === reportId)
  const [status, setStatus] = useState<ReportStatus>(report?.status ?? 'received')
  const [reviewStatus, setReviewStatus] = useState<ReviewStatus>(report?.reviewStatus ?? 'pending')
  const [dialogState, setDialogState] = useState<StatusDialogState>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [statusError, setStatusError] = useState('')

  if (!report) {
    return (
      <section className="admin-card empty-state">
        <strong>제보를 찾을 수 없습니다</strong>
        <Link className="primary-button" to={ROUTE_PATHS.REPORTS}>제보 목록으로 이동</Link>
      </section>
    )
  }

  const nextReportStatus = nextReportStatusMap[status]
  const riskIndicators = report.aiIndicators?.length ? report.aiIndicators : report.riskTags
  const aiTimelineState: TimelineState = report.aiAnalysisStatus === 'queued' || report.aiAnalysisStatus === 'analyzing' ? 'current' : 'complete'
  const reviewTimelineState: TimelineState = reviewStatus === 'rejected' ? 'failed' : reviewStatus === 'pending' ? 'current' : 'complete'
  const processingTimelineState: TimelineState = reviewStatus !== 'approved' ? 'pending' : status === 'completed' ? 'complete' : 'current'

  const openReviewDialog = (nextReviewStatus: ReviewStatus) => {
    if (canChangeReviewStatus(reviewStatus, nextReviewStatus)) setDialogState({ type: 'review', nextReviewStatus })
  }

  const openProcessingDialog = () => {
    if (nextReportStatus && canChangeReportStatus(reviewStatus, status, nextReportStatus)) {
      setDialogState({ type: 'processing', nextStatus: nextReportStatus })
    }
  }

  const confirmStatusChange = async () => {
    if (!dialogState || isSubmitting) return
    setIsSubmitting(true)
    setStatusError('')

    try {
      if (dialogState.type === 'review' && canChangeReviewStatus(reviewStatus, dialogState.nextReviewStatus)) {
        setReviewStatus(dialogState.nextReviewStatus)
      } else if (dialogState.type === 'processing' && canChangeReportStatus(reviewStatus, status, dialogState.nextStatus)) {
        setStatus(dialogState.nextStatus)
      } else {
        throw new Error('변경할 수 없는 상태입니다.')
      }
      setDialogState(null)
    } catch (error) {
      setStatusError(error instanceof Error ? error.message : '상태 변경에 실패했습니다.')
    } finally {
      setIsSubmitting(false)
    }
  }

  const isApprovalDialog = dialogState?.type === 'review' && dialogState.nextReviewStatus === 'approved'
  const dialogTitle = dialogState?.type === 'review'
    ? `제보를 ${isApprovalDialog ? '승인' : '반려'}하시겠습니까?`
    : '제보 처리 상태를 변경하시겠습니까?'

  return (
    <section className="page-stack report-detail-page">
      <nav className="breadcrumb" aria-label="제보 상세 경로">
        <Link to={ROUTE_PATHS.REPORTS}>제보 목록</Link><span aria-hidden="true">/</span>
        <span>제보 상세</span><span aria-hidden="true">/</span>
        <strong aria-current="page">제보 아이디 #{reportId}</strong>
      </nav>

      <div className="report-detail-grid report-detail-grid--first">
        <AdminSection title="제보 이미지">
          {report.imageUrl ? (
            <img className="report-image" src={report.imageUrl} alt={`제보 #${report.id} 현장`} />
          ) : (
            <div className="report-image-placeholder" role="img" aria-label={`제보 #${report.id} 이미지 없음`}><span>제보 이미지가 없습니다.</span></div>
          )}
        </AdminSection>

        <AdminSection title="제보 정보 요약">
          <dl className="report-summary">
            <div><dt>제보 ID</dt><dd>#{report.id}</dd></div>
            <div><dt>제보자명</dt><dd>{report.reporterName || '정보 없음'}</dd></div>
            <div><dt>카테고리</dt><dd>{reportTypeLabels[report.type]}</dd></div>
            <div><dt>제보 시간</dt><dd>{formatAdminDate(report.reportedAt)}</dd></div>
            <div className="report-summary__group">
              <dt>위험 정보</dt>
              <dd><span>위험도</span><RiskBadge value={report.riskLevel} /></dd>
              <dd><span>AI 분석 상태</span><StatusBadge tone={getAiStatusTone(report.aiAnalysisStatus)}>{aiAnalysisStatusLabels[report.aiAnalysisStatus]}</StatusBadge></dd>
              <dd><span>위험 점수</span><strong>{report.riskScore}점</strong></dd>
            </div>
            <div className="report-summary__group">
              <dt>처리 정보</dt>
              <dd><span>검토 상태</span><StatusBadge tone={getReviewStatusTone(reviewStatus)}>{reviewStatusLabels[reviewStatus]}</StatusBadge></dd>
              <dd><span>처리 상태</span><StatusBadge tone={getReportStatusTone(status)}>{reportStatusLabels[status]}</StatusBadge></dd>
              <dd><span>담당 관리자명</span><strong>{report.managerName || '정보 없음'}</strong></dd>
            </div>
            <div className="report-summary__address"><dt>주소</dt><dd>{report.location || '정보 없음'}</dd></div>
          </dl>
        </AdminSection>
      </div>

      <div className="report-detail-grid report-detail-grid--second">
        <AdminSection title="위험도 지표"><RiskLevelIndicator level={report.riskLevel} score={report.riskScore} /></AdminSection>

        <AdminSection title="제보 상태 관리 타임라인">
          {statusError && <p className="login-error" role="alert">{statusError}</p>}
          <ol className="status-timeline">
            <TimelineStep title="제보 접수" state="complete"><time>{formatAdminDate(report.reportedAt)}</time></TimelineStep>
            <TimelineStep title="AI 상황 분석" state={aiTimelineState}>
              <StatusBadge tone={getAiStatusTone(report.aiAnalysisStatus)}>{aiAnalysisStatusLabels[report.aiAnalysisStatus]}</StatusBadge>
              <p>위험 점수 {report.riskScore}점</p>
              {report.aiCompletedAt && <time>{formatAdminDate(report.aiCompletedAt)}</time>}
            </TimelineStep>
            <TimelineStep title="검토" state={reviewTimelineState}>
              <StatusBadge tone={getReviewStatusTone(reviewStatus)}>{reviewStatusLabels[reviewStatus]}</StatusBadge>
              {reviewStatus === 'pending' ? (
                <div className="button-row">
                  <button type="button" className="primary-button" disabled={isSubmitting} onClick={() => openReviewDialog('approved')}>승인</button>
                  <button type="button" className="danger-outline-button" disabled={isSubmitting} onClick={() => openReviewDialog('rejected')}>반려</button>
                </div>
              ) : <p>검토 결과가 확정되었습니다.</p>}
            </TimelineStep>
            <TimelineStep title="처리 상태" state={processingTimelineState}>
              <div className="timeline-status-row"><StatusBadge tone={getReportStatusTone(status)}>{reportStatusLabels[status]}</StatusBadge><span>담당 {report.managerName || '정보 없음'}</span></div>
              {report.history.length > 0 && <ul className="timeline-history">{report.history.map((history) => <li key={history.id}><strong>{history.action}</strong><span>{history.actor}</span><time>{formatAdminDate(history.createdAt)}</time></li>)}</ul>}
              {nextReportStatus && <button type="button" className="primary-button" disabled={reviewStatus !== 'approved' || isSubmitting} onClick={openProcessingDialog}>{reportStatusLabels[nextReportStatus]}으로 변경</button>}
            </TimelineStep>
          </ol>
        </AdminSection>
      </div>

      <AdminSection title="AI 상황 요약">
        <div className="ai-summary-layout">
          <div className="ai-summary-metrics">
            <div><span>위험 단계</span><RiskBadge value={report.riskLevel} /></div>
            <div><span>위험 점수</span><strong>{report.riskScore}점</strong></div>
          </div>
          <section><h3>주요 위험 지표</h3>{riskIndicators.length ? <ul>{riskIndicators.map((indicator) => <li key={indicator}>{indicator}</li>)}</ul> : <p>AI 분석 결과가 아직 없습니다.</p>}</section>
          <section><h3>상황 요약</h3><p className="break-text">{report.aiSummary || 'AI 분석 결과가 아직 없습니다.'}</p></section>
          <section><h3>태그</h3>{report.riskTags.length ? <div className="tag-list">{report.riskTags.map((tag) => <span key={tag}>#{tag.replace(/^#/, '')}</span>)}</div> : <p>관련 태그가 없습니다.</p>}</section>
        </div>
      </AdminSection>

      <AdminModal
        open={dialogState !== null}
        title={dialogTitle}
        onClose={() => { if (!isSubmitting) setDialogState(null) }}
        footer={<><button type="button" className="secondary-button" disabled={isSubmitting} onClick={() => setDialogState(null)}>취소</button><button type="button" className={dialogState?.type === 'review' && !isApprovalDialog ? 'danger-button' : 'primary-button'} disabled={isSubmitting} onClick={confirmStatusChange}>{isSubmitting ? '처리 중...' : dialogState?.type === 'review' ? `${isApprovalDialog ? '승인' : '반려'}하기` : '상태 변경'}</button></>}
      >
        {dialogState?.type === 'review' ? <p>이 제보를 {isApprovalDialog ? '승인' : '반려'}하시겠습니까?<br />{isApprovalDialog ? '승인' : '반려'} 후에는 검토 상태가 변경됩니다.</p> : <p>현재 {reportStatusLabels[status]} 상태를 {dialogState ? reportStatusLabels[dialogState.nextStatus] : ''} 상태로 변경합니다.</p>}
      </AdminModal>
    </section>
  )
}
