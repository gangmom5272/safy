import { useEffect, useState } from 'react'
import { AdminModal } from '../../components/admin/AdminModal'
import {
  completeReport,
  confirmReport,
  getTransferTargets,
  reassignReport,
  rejectReport,
  transferReport,
  type AssignmentStatus,
  type TransferTarget,
} from './reportApi'

// 승인(confirm)은 지도 반영, 종료(complete)는 대응 종결이다. 예전 '처리 완료' 하나가
// 두 일을 겸했는데, 승인과 종료가 갈라지며 버튼도 나뉘었다.
export type OperatorAction = 'confirm' | 'complete' | 'reject' | 'reassign' | 'transfer'

const ACTION_LABELS: Record<OperatorAction, string> = {
  confirm: '제보 승인',
  complete: '상황 종료',
  reject: '반려',
  reassign: '재배정',
  transfer: '이관',
}

const ACTION_TITLES: Record<OperatorAction, string> = {
  confirm: '이 제보를 승인하시겠습니까?',
  complete: '이 상황을 종료하시겠습니까?',
  reject: '이 제보를 반려하시겠습니까?',
  reassign: '우리 센터 다른 담당자에게 넘기시겠습니까?',
  transfer: '주변 다른 관제센터로 이관하시겠습니까?',
}

const ACTION_CONFIRM: Record<OperatorAction, string> = {
  confirm: '승인',
  complete: '종료',
  reject: '반려',
  reassign: '넘기기',
  transfer: '이관',
}

/** 사유가 필요 없는 액션의 모달 본문. 무엇이 일어나는지 한 문장으로 알린다. */
const ACTION_BODIES: Partial<Record<OperatorAction, string>> = {
  confirm: '승인하면 제보가 사용자 지도에 마커와 격자 위험도로 반영됩니다. 담당과 라이브 연결은 유지됩니다.',
  complete: '대응을 마치고 제보를 종결합니다. 라이브 연결이 종료되고 제보자에게 통지됩니다.',
}

const REASON_PLACEHOLDERS: Record<OperatorAction, string> = {
  confirm: '',
  complete: '',
  reject: '예) 현장 확인 결과 위험 상황이 아님',
  reassign: '예) 관할 구역 오배정',
  transfer: '예) 관할 경계 지역이라 인접 센터가 대응',
}

const REASON_MAX_LENGTH = 500

function ConfirmIcon() {
  return (
    <svg className="report-action-icon" viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false">
      <circle cx="12" cy="12" r="8.5" />
      <path d="M8.5 12.2l2.5 2.5 4.5-5" />
    </svg>
  )
}

function CompleteIcon() {
  return (
    <svg className="report-action-icon" viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false">
      <circle cx="12" cy="12" r="8.5" />
      <path d="M9 9.5h6v5H9z" />
    </svg>
  )
}

function ReassignIcon() {
  return (
    <svg className="report-action-icon" viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false">
      <circle cx="9" cy="8" r="3.2" />
      <path d="M3.5 19a5.5 5.5 0 0 1 11 0" />
      <path d="M16.5 6.5a3 3 0 0 1 0 5.6M18 19a5.6 5.6 0 0 0-2.2-4.4" />
    </svg>
  )
}

function TransferIcon() {
  return (
    <svg className="report-action-icon" viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false">
      <path d="M4 9h14l-3-3M20 15H6l3 3" />
    </svg>
  )
}

function RejectIcon() {
  return (
    <svg className="report-action-icon" viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false">
      <circle cx="12" cy="12" r="8.5" />
      <path d="M6.5 6.5l11 11" />
    </svg>
  )
}

const REASON_REQUIRED: Record<OperatorAction, boolean> = {
  confirm: false,
  complete: false,
  reject: true,
  reassign: true,
  transfer: true,
}

interface ReportActionPanelProps {
  reportId: number
  /** 조회한 담당자 본인의 배정 상태. 진행 중일 때만 처리 버튼을 연다. */
  assignmentStatus: AssignmentStatus | null
  /** 이미 승인된 제보에는 승인 버튼을 다시 보여주지 않는다. */
  isConfirmed: boolean
  /** 어떤 액션이 끝났는지 알려준다. 승인은 화면에 남아 계속 모니터링하고, 나머지는 목록으로 돌아간다. */
  onActionComplete: (action: OperatorAction) => void
  /**
   * 버튼 배치. 'stack' 은 좁은 카드 안에 세로로 쌓는 기본형(모니터링 화면),
   * 'row' 는 상단 바처럼 가로로 넓은 자리에서 한 줄로 세우는 형태다.
   */
  layout?: 'stack' | 'row'
}

const ACTIONABLE_STATUSES: AssignmentStatus[] = ['ASSIGNED', 'ACCEPTED', 'IN_PROGRESS']

/**
 * 배치별 버튼 순서. CSS order 로 눈에 보이는 순서만 바꾸면 키보드 이동 순서가 화면과
 * 어긋나므로 DOM 순서 자체를 바꾼다.
 */
const ACTION_ORDER: Record<'stack' | 'row', OperatorAction[]> = {
  // 세로로 쌓을 때는 진행 순서대로 두고, 되돌릴 수 없는 반려를 맨 아래에 둔다.
  stack: ['confirm', 'complete', 'reassign', 'transfer', 'reject'],
  // 두 줄 배치. 첫 줄은 이 제보의 성격을 가르는 두 갈래(승인·반려), 둘째 줄은 담당을
  // 넘기거나 대응을 마치는 조치다.
  row: ['confirm', 'reject', 'transfer', 'reassign', 'complete'],
}

function ActionIcon({ action }: { action: OperatorAction }) {
  if (action === 'confirm') return <ConfirmIcon />
  if (action === 'complete') return <CompleteIcon />
  if (action === 'reassign') return <ReassignIcon />
  if (action === 'transfer') return <TransferIcon />
  return <RejectIcon />
}

function actionButtonClass(action: OperatorAction, isConfirmed: boolean) {
  if (action === 'confirm') return 'primary-button'
  if (action === 'reject') return 'danger-button'
  // 이미 승인된 제보에서는 종료가 다음 기본 조치가 된다.
  if (action === 'complete' && isConfirmed) return 'primary-button'
  return 'secondary-button'
}

function targetHint(target: TransferTarget) {
  const role = target.coverageRole === 'PRIMARY' ? '주 담당' : '예비 담당'
  if (target.visited && !target.hasOnDutyOperator) return `${role} · 이미 거쳐온 센터, 근무자 없음`
  if (target.visited) return `${role} · 이미 거쳐온 센터`
  if (!target.hasOnDutyOperator) return `${role} · 지금 근무 중인 담당자 없음`
  return `${role} · 근무 중`
}

export function ReportActionPanel({
  reportId,
  assignmentStatus,
  isConfirmed,
  onActionComplete,
  layout = 'stack',
}: ReportActionPanelProps) {
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
      if (action === 'confirm') await confirmReport(reportId)
      if (action === 'complete') await completeReport(reportId)
      if (action === 'reject') await rejectReport(reportId, trimmedReason)
      if (action === 'reassign') await reassignReport(reportId, trimmedReason)
      if (action === 'transfer' && targetCenterId !== null) {
        await transferReport(reportId, targetCenterId, trimmedReason)
      }
      const finished = action
      // 승인은 화면에 남으므로 모달을 닫고 다시 조작할 수 있게 되돌린다.
      setAction(null)
      setIsSubmitting(false)
      onActionComplete(finished)
    } catch (error) {
      setActionError(error instanceof Error ? error.message : '처리하지 못했습니다.')
      setIsSubmitting(false)
    }
  }

  // 종결된 배정이거나 배정이 없는 계정(SUPER_ADMIN)은 처리 API 가 404 를 돌려주므로
  // 버튼을 아예 노출하지 않는다.
  if (!assignmentStatus || !ACTIONABLE_STATUSES.includes(assignmentStatus)) {
    return (
      <p className={`report-action-closed ${layout === 'row' ? 'report-action-closed--row' : ''}`} role="status">
        {assignmentStatus
          ? '이미 대응이 끝난 제보입니다. 더 이상 처리할 수 없습니다.'
          : '이 제보에 배정된 담당자만 처리할 수 있습니다.'}
      </p>
    )
  }

  const visibleActions = ACTION_ORDER[layout].filter(
    (candidate) => candidate !== 'confirm' || !isConfirmed,
  )

  return (
    <>
      {actionError && <p className="login-error" role="alert">{actionError}</p>}
      {/* stack 은 좁은 카드 안에서 세로로 쌓아 너비·높이를 맞추고, row 는 넓은 자리에서
          두 줄로 나눈다. 승인은 한 번이면 충분해 이미 승인된 제보에서는 빠진다 —
          몇 개가 남는지에 따라 줄 나누기가 달라지므로 개수를 CSS 에 넘긴다. */}
      <div
        className={`report-action-buttons ${layout === 'row' ? 'report-action-buttons--row' : ''}`}
        data-count={visibleActions.length}
      >
        {visibleActions.map((visibleAction) => (
          <button
            key={visibleAction}
            type="button"
            className={actionButtonClass(visibleAction, isConfirmed)}
            disabled={isSubmitting}
            onClick={() => selectAction(visibleAction)}
          >
            <ActionIcon action={visibleAction} />{ACTION_LABELS[visibleAction]}
          </button>
        ))}
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
          <>
            <p>{action ? ACTION_BODIES[action] : ''}</p>
            {/* 승인을 건너뛴 종료는 되돌릴 수 없다. 종료된 제보는 승인할 수 없어서
                그 제보는 사용자 지도에 한 번도 뜨지 않은 채 끝난다. */}
            {action === 'complete' && !isConfirmed && (
              <p className="notice-text notice-text--warning" role="alert">
                이 제보는 아직 승인되지 않았습니다. 지금 종료하면 사용자 지도에 반영되지 않은 채
                끝나며, 종료한 뒤에는 승인할 수 없습니다.
              </p>
            )}
          </>
        )}
      </AdminModal>
    </>
  )
}
