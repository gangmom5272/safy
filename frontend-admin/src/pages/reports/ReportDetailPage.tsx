import { Link, useParams } from 'react-router'
import { useState } from 'react'
import {
  adminReports,
  aiAnalysisStatusLabels,
  reportStatusLabels,
  reportTypeLabels,
  reviewStatusLabels,
  riskLevelLabels,
} from '../../mocks/adminMockData'
import { ROUTE_PATHS } from '../../router/paths'
import type { ReportStatus, ReviewStatus } from '../../types/admin'

type StatusDialogState =
  | { type: 'review'; nextReviewStatus: Extract<ReviewStatus, 'approved' | 'rejected'> }
  | { type: 'processing'; nextStatus: ReportStatus }
  | null

const reviewStatusDescriptions: Record<ReviewStatus, string> = {
  pending: '관리자가 현장 상황과 AI 분석 결과를 검토하기 전입니다.',
  approved: '제보가 유효한 것으로 확정되어 처리 상태를 진행할 수 있습니다.',
  rejected: '제보가 반려되어 처리 상태를 더 이상 변경할 수 없습니다.',
}

const reportStatusDescriptions: Record<ReportStatus, string> = {
  received: '제보가 접수되어 초기 확인을 기다리는 상태입니다.',
  checking: '담당자가 현장 상황과 제보 내용을 확인 중인 상태입니다.',
  in_progress: '현장 조치 또는 관계 기관 처리가 진행 중인 상태입니다.',
  completed: '필요한 조치가 완료되어 처리 상태가 확정된 상태입니다.',
}

const nextReportStatusMap: Partial<Record<ReportStatus, ReportStatus>> = {
  received: 'checking',
  checking: 'in_progress',
  in_progress: 'completed',
}

function canChangeReviewStatus(
  currentStatus: ReviewStatus,
  nextStatus: ReviewStatus,
): nextStatus is Extract<ReviewStatus, 'approved' | 'rejected'> {
  return currentStatus === 'pending' && (nextStatus === 'approved' || nextStatus === 'rejected')
}

function canChangeReportStatus(
  reviewStatus: ReviewStatus,
  currentStatus: ReportStatus,
  nextStatus: ReportStatus,
) {
  return reviewStatus === 'approved' && nextReportStatusMap[currentStatus] === nextStatus
}

function StatusBadge({ children }: { children: string }) {
  return <strong className="status-badge">{children}</strong>
}

export function ReportDetailPage() {
  const { reportId } = useParams()
  const report = adminReports.find((item) => String(item.id) === reportId)
  const [status, setStatus] = useState<ReportStatus>(report?.status ?? 'received')
  const [reviewStatus, setReviewStatus] = useState<ReviewStatus>(
    report?.reviewStatus ?? 'pending',
  )
  const [dialogState, setDialogState] = useState<StatusDialogState>(null)

  const nextReportStatus = nextReportStatusMap[status]
  const isReviewPending = reviewStatus === 'pending'
  const canRequestProcessingChange =
    nextReportStatus !== undefined &&
    canChangeReportStatus(reviewStatus, status, nextReportStatus)

  const openReviewDialog = (nextStatus: ReviewStatus) => {
    if (!canChangeReviewStatus(reviewStatus, nextStatus)) {
      return
    }

    setDialogState({ type: 'review', nextReviewStatus: nextStatus })
  }

  const openProcessingDialog = () => {
    if (!nextReportStatus || !canChangeReportStatus(reviewStatus, status, nextReportStatus)) {
      return
    }

    setDialogState({ type: 'processing', nextStatus: nextReportStatus })
  }

  const confirmStatusChange = () => {
    if (!dialogState) {
      return
    }

    if (
      dialogState.type === 'review' &&
      canChangeReviewStatus(reviewStatus, dialogState.nextReviewStatus)
    ) {
      setReviewStatus(dialogState.nextReviewStatus)
      setDialogState(null)
      return
    }

    if (
      dialogState.type === 'processing' &&
      canChangeReportStatus(reviewStatus, status, dialogState.nextStatus)
    ) {
      setStatus(dialogState.nextStatus)
      setDialogState(null)
      return
    }

    setDialogState(null)
  }

  if (!report) {
    return (
      <section className="admin-card empty-state">
        <strong>제보를 찾을 수 없습니다</strong>
        <Link className="primary-button" to={ROUTE_PATHS.REPORTS}>
          제보 목록으로 이동
        </Link>
      </section>
    )
  }

  return (
    <section className="page-stack">
      <article className="admin-card">
        <div className="card-heading">
          <div>
            <h2>제보 ID: {report.id}</h2>
            <p>{report.location}</p>
          </div>
          <Link className="secondary-button" to={ROUTE_PATHS.REPORTS}>
            제보 목록으로 돌아가기
          </Link>
        </div>

        <div className="detail-grid">
          <div>
            <span>제보 유형</span>
            <strong>{reportTypeLabels[report.type]}</strong>
          </div>
          <div>
            <span>위험도</span>
            <strong className={`risk-text risk-${report.riskLevel}`}>
              {riskLevelLabels[report.riskLevel]}
            </strong>
          </div>
          <div>
            <span>위험 점수</span>
            <strong>{report.riskScore}</strong>
          </div>
          <div>
            <span>제보 시간</span>
            <strong>{report.reportedAt}</strong>
          </div>
          <div>
            <span>처리 상태</span>
            <strong>{reportStatusLabels[status] ?? '정보 없음'}</strong>
          </div>
          <div>
            <span>검토 상태</span>
            <strong>{reviewStatusLabels[reviewStatus] ?? '정보 없음'}</strong>
          </div>
          <div>
            <span>AI 분석 상태</span>
            <strong>{aiAnalysisStatusLabels[report.aiAnalysisStatus]}</strong>
          </div>
          <div>
            <span>담당 관리자</span>
            <strong>{report.managerName}</strong>
          </div>
        </div>
      </article>

      <article className="admin-card">
        <h2>AI 상황 요약</h2>
        <p>{report.aiSummary}</p>
        <div className="tag-list">
          {report.riskTags.map((tag) => (
            <span key={tag}>{tag}</span>
          ))}
        </div>
      </article>

      <article className="admin-card">
        <h2>제보 상태 관리</h2>
        <div className="status-management-grid">
          <section className="status-section" aria-labelledby="review-status-title">
            <div>
              <h3 id="review-status-title">검토 상태</h3>
              <p>{reviewStatusDescriptions[reviewStatus] ?? '상태 정보를 확인할 수 없습니다.'}</p>
            </div>
            <div className="status-current">
              <span>현재 상태</span>
              <StatusBadge>{reviewStatusLabels[reviewStatus] ?? '정보 없음'}</StatusBadge>
            </div>
            <div className="button-row">
              <button
                type="button"
                className="primary-button"
                disabled={!isReviewPending}
                onClick={() => openReviewDialog('approved')}
              >
                승인
              </button>
              <button
                type="button"
                className="secondary-button"
                disabled={!isReviewPending}
                onClick={() => openReviewDialog('rejected')}
              >
                반려
              </button>
            </div>
            {!isReviewPending && (
              <p className="notice-text">확정된 검토 결과는 변경할 수 없습니다.</p>
            )}
          </section>

          <section className="status-section" aria-labelledby="processing-status-title">
            <div>
              <h3 id="processing-status-title">처리 상태</h3>
              <p>{reportStatusDescriptions[status] ?? '상태 정보를 확인할 수 없습니다.'}</p>
            </div>
            <div className="status-current">
              <span>현재 상태</span>
              <StatusBadge>{reportStatusLabels[status] ?? '정보 없음'}</StatusBadge>
            </div>
            {nextReportStatus && (
              <div className="status-current">
                <span>다음 단계</span>
                <StatusBadge>{reportStatusLabels[nextReportStatus] ?? '정보 없음'}</StatusBadge>
              </div>
            )}
            {reviewStatus === 'pending' && (
              <p className="notice-text">제보 승인 후 처리 상태를 변경할 수 있습니다.</p>
            )}
            {reviewStatus === 'rejected' && (
              <p className="notice-text">반려된 제보는 처리 상태를 변경할 수 없습니다.</p>
            )}
            {reviewStatus === 'approved' && status === 'completed' && (
              <p className="notice-text">처리가 완료되어 상태를 변경할 수 없습니다.</p>
            )}
            {nextReportStatus ? (
              <button
                type="button"
                className="primary-button"
                disabled={!canRequestProcessingChange}
                onClick={openProcessingDialog}
              >
                {reportStatusLabels[nextReportStatus]}으로 변경
              </button>
            ) : (
              <button type="button" className="secondary-button" disabled>
                상태 변경 불가
              </button>
            )}
          </section>
        </div>
      </article>

      <article className="admin-card">
        <h2>처리 이력</h2>
        <ol className="history-list">
          {report.history.map((history) => (
            <li key={history.id}>
              <strong>{history.action}</strong>
              <span>{history.actor}</span>
              <time>{history.createdAt}</time>
            </li>
          ))}
        </ol>
      </article>

      <ConfirmStatusDialog
        dialogState={dialogState}
        currentStatus={status}
        onCancel={() => setDialogState(null)}
        onConfirm={confirmStatusChange}
      />
    </section>
  )
}

interface ConfirmStatusDialogProps {
  dialogState: StatusDialogState
  currentStatus: ReportStatus
  onCancel: () => void
  onConfirm: () => void
}

function ConfirmStatusDialog({
  dialogState,
  currentStatus,
  onCancel,
  onConfirm,
}: ConfirmStatusDialogProps) {
  if (!dialogState) {
    return null
  }

  if (dialogState.type === 'review') {
    const isApproval = dialogState.nextReviewStatus === 'approved'

    return (
      <div className="dialog-backdrop" role="presentation">
        <section
          className="confirm-dialog"
          role="dialog"
          aria-modal="true"
          aria-labelledby="status-dialog-title"
        >
          <h2 id="status-dialog-title">
            제보를 {isApproval ? '승인' : '반려'}하시겠습니까?
          </h2>
          <p>
            {isApproval
              ? '승인 후에는 검토 결과를 변경할 수 없습니다.'
              : '반려 후에는 검토 결과를 변경할 수 없습니다.'}
          </p>
          <p>
            {isApproval
              ? '현장 상황과 AI 분석 결과를 다시 확인해주세요.'
              : '반려 여부를 다시 확인해주세요.'}
          </p>
          <div className="dialog-actions">
            <button type="button" className="secondary-button" onClick={onCancel}>
              취소
            </button>
            <button type="button" className="primary-button" onClick={onConfirm}>
              {isApproval ? '승인 확정' : '반려 확정'}
            </button>
          </div>
        </section>
      </div>
    )
  }

  const isCompleting = dialogState.nextStatus === 'completed'

  return (
    <div className="dialog-backdrop" role="presentation">
      <section
        className="confirm-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="status-dialog-title"
      >
        <h2 id="status-dialog-title">
          {isCompleting ? '제보 처리를 완료하시겠습니까?' : '제보 처리 상태를 변경하시겠습니까?'}
        </h2>
        {isCompleting ? (
          <>
            <p>처리 완료 후에는 상태를 변경할 수 없습니다.</p>
            <p>모든 조치가 완료되었는지 확인해주세요.</p>
          </>
        ) : (
          <>
            <dl className="dialog-status-list">
              <div>
                <dt>현재 상태</dt>
                <dd>{reportStatusLabels[currentStatus] ?? '정보 없음'}</dd>
              </div>
              <div>
                <dt>변경 상태</dt>
                <dd>{reportStatusLabels[dialogState.nextStatus] ?? '정보 없음'}</dd>
              </div>
            </dl>
            <p>이전 상태로 되돌릴 수 없습니다.</p>
            <p>실제 처리 진행 상황을 확인한 후 변경해주세요.</p>
          </>
        )}
        <div className="dialog-actions">
          <button type="button" className="secondary-button" onClick={onCancel}>
            취소
          </button>
          <button type="button" className="primary-button" onClick={onConfirm}>
            {isCompleting ? '처리 완료' : '상태 변경'}
          </button>
        </div>
      </section>
    </div>
  )
}
