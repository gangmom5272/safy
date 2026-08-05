import { useEffect, useState } from 'react'
import { AdminModal } from '../../components/admin/AdminModal'
import {
  completeReport,
  getTransferTargets,
  reassignReport,
  rejectReport,
  transferReport,
  type AssignmentStatus,
  type TransferTarget,
} from './reportApi'

type OperatorAction = 'complete' | 'reject' | 'reassign' | 'transfer'

const ACTION_LABELS: Record<OperatorAction, string> = {
  complete: '처리 완료',
  reject: '반려',
  reassign: '재배정',
  transfer: '이관',
}

const ACTION_TITLES: Record<OperatorAction, string> = {
  complete: '해당 제보를 확인하셨습니까?',
  reject: '이 제보를 반려하시겠습니까?',
  reassign: '우리 센터 다른 담당자에게 넘기시겠습니까?',
  transfer: '주변 다른 관제센터로 이관하시겠습니까?',
}

const ACTION_CONFIRM: Record<OperatorAction, string> = {
  complete: '완료',
  reject: '반려',
  reassign: '넘기기',
  transfer: '이관',
}

const REASON_PLACEHOLDERS: Record<OperatorAction, string> = {
  complete: '',
  reject: '예) 현장 확인 결과 위험 상황이 아님',
  reassign: '예) 관할 구역 오배정',
  transfer: '예) 관할 경계 지역이라 인접 센터가 대응',
}

const REASON_MAX_LENGTH = 500

const REASON_REQUIRED: Record<OperatorAction, boolean> = {
  complete: false,
  reject: true,
  reassign: true,
  transfer: true,
}

interface ReportActionPanelProps {
  reportId: number
  /** 조회한 담당자 본인의 배정 상태. 진행 중일 때만 처리 버튼을 연다. */
  assignmentStatus: AssignmentStatus | null
  onActionComplete: () => void
}

const ACTIONABLE_STATUSES: AssignmentStatus[] = ['ASSIGNED', 'ACCEPTED', 'IN_PROGRESS']

function targetHint(target: TransferTarget) {
  const role = target.coverageRole === 'PRIMARY' ? '주 담당' : '예비 담당'
  if (target.visited && !target.hasOnDutyOperator) return `${role} · 이미 거쳐온 센터, 근무자 없음`
  if (target.visited) return `${role} · 이미 거쳐온 센터`
  if (!target.hasOnDutyOperator) return `${role} · 지금 근무 중인 담당자 없음`
  return `${role} · 근무 중`
}

export function ReportActionPanel({ reportId, assignmentStatus, onActionComplete }: ReportActionPanelProps) {
  const [action, setAction] = useState<OperatorAction | null>(null)
  const [reason, setReason] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [actionError, setActionError] = useState('')
  const [targets, setTargets] = useState<TransferTarget[] | null>(null)
  const [selectedCenterId, setSelectedCenterId] = useState<number | null>(null)

  // 이관 모달을 열 때만 선택지를 받아온다. 목록은 근무 상태에 따라 바뀌므로 캐시하지 않는다.
  useEffect(() => {
    if (action !== 'transfer') return

    let cancelled = false
    getTransferTargets(reportId)
      .then((loaded) => {
        if (!cancelled) setTargets(loaded)
      })
      .catch((error: unknown) => {
        if (cancelled) return
        setTargets([])
        setActionError(error instanceof Error ? error.message : '이관 대상을 불러오지 못했습니다.')
      })

    return () => {
      cancelled = true
    }
  }, [action, reportId])

  const selectAction = (nextAction: OperatorAction) => {
    setAction(nextAction)
    setReason('')
    setActionError('')
    setSelectedCenterId(null)
    // 근무 상태는 수시로 바뀌므로 모달을 열 때마다 다시 받는다(로딩 표시도 겸한다).
    setTargets(null)
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
    const targetCenterId = selectedCenterId
    if (action === 'transfer' && targetCenterId === null) {
      setActionError('이관할 관제센터를 선택해주세요.')
      return
    }

    setIsSubmitting(true)
    setActionError('')
    try {
      if (action === 'complete') await completeReport(reportId)
      if (action === 'reject') await rejectReport(reportId, trimmedReason)
      if (action === 'reassign') await reassignReport(reportId, trimmedReason)
      if (action === 'transfer' && targetCenterId !== null) {
        await transferReport(reportId, targetCenterId, trimmedReason)
      }
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
      <div className="button-row report-action-buttons">
        <button type="button" className="primary-button" disabled={isSubmitting} onClick={() => selectAction('complete')}>
          {ACTION_LABELS.complete}
        </button>
        <button type="button" className="secondary-button" disabled={isSubmitting} onClick={() => selectAction('reassign')}>
          {ACTION_LABELS.reassign}
        </button>
        <button type="button" className="secondary-button" disabled={isSubmitting} onClick={() => selectAction('transfer')}>
          {ACTION_LABELS.transfer}
        </button>
        <button type="button" className="danger-button" disabled={isSubmitting} onClick={() => selectAction('reject')}>
          {ACTION_LABELS.reject}
        </button>
      </div>

      <AdminModal
        open={action !== null}
        title={action ? ACTION_TITLES[action] : ''}
        onClose={closeModal}
        footer={(
          <>
            <button type="button" className="secondary-button" disabled={isSubmitting} onClick={closeModal}>취소</button>
            <button type="button" className={action === 'reject' ? 'danger-button' : 'primary-button'} disabled={isSubmitting} onClick={runAction}>
              {isSubmitting ? '처리 중...' : action ? ACTION_CONFIRM[action] : ''}
            </button>
          </>
        )}
      >
        {action === 'transfer' && (
          targets === null ? (
            <p role="status">이관 대상을 불러오는 중...</p>
          ) : targets.length === 0 ? (
            <p role="status">이 제보를 넘길 수 있는 다른 관제센터가 없습니다.</p>
          ) : (
            <ul className="transfer-target-list">
              {targets.map((target) => (
                <li key={target.controlCenterId}>
                  <label className={target.transferable ? undefined : 'transfer-target--disabled'}>
                    <input
                      type="radio"
                      name="transfer-target"
                      value={target.controlCenterId}
                      checked={selectedCenterId === target.controlCenterId}
                      disabled={!target.transferable || isSubmitting}
                      onChange={() => setSelectedCenterId(target.controlCenterId)}
                    />
                    <span>
                      <strong>{target.controlCenterName}</strong>
                      <small>{targetHint(target)}</small>
                    </span>
                  </label>
                </li>
              ))}
            </ul>
          )
        )}

        {/* 반려·재배정·이관이 같은 사유 입력 양식을 쓴다. 라벨을 위, 입력칸을 아래 전체 너비로
            두어 세 모달의 폼이 같은 모양이 되게 한다. */}
        {action && REASON_REQUIRED[action] ? (
          <label className="admin-field">
            <span className="admin-field__label">사유 <em>(필수)</em></span>
            <textarea
              className="admin-field__control"
              value={reason}
              rows={4}
              maxLength={REASON_MAX_LENGTH}
              onChange={(event) => setReason(event.target.value)}
              placeholder={REASON_PLACEHOLDERS[action]}
            />
            <span className="admin-field__counter">{reason.length} / {REASON_MAX_LENGTH}</span>
          </label>
        ) : (
          <p>제보를 종료하면 사용자 지도에 반영됩니다.</p>
        )}
      </AdminModal>
    </>
  )
}
