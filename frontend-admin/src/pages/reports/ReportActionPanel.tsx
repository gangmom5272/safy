import { useState } from 'react'
import { AdminModal } from '../../components/admin/AdminModal'
import { completeReport, reassignReport, rejectReport, type AssignmentStatus } from './reportApi'

type OperatorAction = 'complete' | 'reject' | 'reassign'

const ACTION_LABELS: Record<OperatorAction, string> = {
  complete: '업무 종료',
  reject: '반려',
  reassign: '재배정 요청',
}

const REASON_REQUIRED: Record<OperatorAction, boolean> = {
  complete: false,
  reject: true,
  reassign: true,
}

interface ReportActionPanelProps {
  reportId: number
  /** 조회한 담당자 본인의 배정 상태. 진행 중일 때만 처리 버튼을 연다. */
  assignmentStatus: AssignmentStatus | null
  onActionComplete: () => void
}

const ACTIONABLE_STATUSES: AssignmentStatus[] = ['ASSIGNED', 'ACCEPTED', 'IN_PROGRESS']

export function ReportActionPanel({ reportId, assignmentStatus, onActionComplete }: ReportActionPanelProps) {
  const [action, setAction] = useState<OperatorAction | null>(null)
  const [reason, setReason] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [actionError, setActionError] = useState('')

  const selectAction = (nextAction: OperatorAction) => {
    setAction(nextAction)
    setReason('')
    setActionError('')
  }

  const closeModal = () => {
    if (isSubmitting) return
    setAction(null)
    setActionError('')
  }

  const runAction = async () => {
    if (!action || isSubmitting) return

    const trimmedReason = reason.trim()
    if (REASON_REQUIRED[action] && !trimmedReason) {
      setActionError('사유를 입력해주세요.')
      return
    }

    setIsSubmitting(true)
    setActionError('')
    try {
      if (action === 'complete') await completeReport(reportId)
      if (action === 'reject') await rejectReport(reportId, trimmedReason)
      if (action === 'reassign') await reassignReport(reportId, trimmedReason)
      onActionComplete()
    } catch (error) {
      setActionError(error instanceof Error ? error.message : '처리하지 못했습니다.')
      setIsSubmitting(false)
    }
  }

  // 종결된 배정이거나 배정이 없는 계정(SUPER_ADMIN)은 처리 API 가 404 를 돌려주므로
  // 버튼을 아예 노출하지 않는다.
  if (!assignmentStatus || !ACTIONABLE_STATUSES.includes(assignmentStatus)) {
    return (
      <p className="report-action-closed" role="status">
        {assignmentStatus
          ? '이미 대응이 끝난 제보입니다. 더 이상 처리할 수 없습니다.'
          : '이 제보에 배정된 담당자만 처리할 수 있습니다.'}
      </p>
    )
  }

  return (
    <>
      {actionError && <p className="login-error" role="alert">{actionError}</p>}
      <p>제보를 확인한 담당자가 처리 완료·반려 또는 재배정을 결정할 수 있습니다.</p>
      <div className="button-row">
        <button type="button" className="primary-button" disabled={isSubmitting} onClick={() => selectAction('complete')}>
          {ACTION_LABELS.complete}
        </button>
        <button type="button" className="secondary-button" disabled={isSubmitting} onClick={() => selectAction('reassign')}>
          {ACTION_LABELS.reassign}
        </button>
        <button type="button" className="danger-outline-button" disabled={isSubmitting} onClick={() => selectAction('reject')}>
          {ACTION_LABELS.reject}
        </button>
      </div>

      <AdminModal
        open={action !== null}
        title={action ? `${ACTION_LABELS[action]} 하시겠습니까?` : ''}
        onClose={closeModal}
        footer={(
          <>
            <button type="button" className="secondary-button" disabled={isSubmitting} onClick={closeModal}>취소</button>
            <button type="button" className={action === 'reject' ? 'danger-button' : 'primary-button'} disabled={isSubmitting} onClick={runAction}>
              {isSubmitting ? '처리 중...' : action ? ACTION_LABELS[action] : ''}
            </button>
          </>
        )}
      >
        {action && REASON_REQUIRED[action] ? (
          <label>
            사유 (필수)
            <textarea
              value={reason}
              maxLength={500}
              onChange={(event) => setReason(event.target.value)}
              placeholder="예) 관할 구역 오배정"
            />
          </label>
        ) : (
          <p>제보를 종료하면 관제센터 라우팅과 진행 중인 라이브 세션도 함께 종료됩니다.</p>
        )}
      </AdminModal>
    </>
  )
}
