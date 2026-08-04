import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { AdminModal } from '../../components/admin/AdminModal'
import { AdminSection } from '../../components/admin/AdminSection'
import { StatusBadge } from '../../components/admin/StatusBadge'
import { formatAdminDate } from '../../utils/adminDate'
import {
  changeOperatorStatus,
  createOperator,
  getControlCenters,
  getOperators,
  membershipRoleLabel,
  updateOperator,
  MEMBERSHIP_ROLE_OPTIONS,
  OPERATOR_ROLE_LABELS,
  OPERATOR_STATUS_LABELS,
  type ControlCenterSummary,
  type OperatorSummary,
} from './operatorApi'

const PAGE_SIZE = 20

interface OperatorFormState {
  loginId: string
  password: string
  displayName: string
  controlCenterId: string
  membershipRole: string
}

const emptyForm: OperatorFormState = {
  loginId: '',
  password: '',
  displayName: '',
  controlCenterId: '',
  membershipRole: MEMBERSHIP_ROLE_OPTIONS[0].value,
}

function toErrorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

export function OperatorManagementPage() {
  const [operators, setOperators] = useState<OperatorSummary[]>([])
  const [centers, setCenters] = useState<ControlCenterSummary[]>([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState('')

  const [editTarget, setEditTarget] = useState<OperatorSummary | null>(null)
  const [isCreateOpen, setIsCreateOpen] = useState(false)
  const [form, setForm] = useState<OperatorFormState>(emptyForm)
  const [formError, setFormError] = useState('')
  const [isSaving, setIsSaving] = useState(false)
  const [statusChangingId, setStatusChangingId] = useState<number | null>(null)
  const [notice, setNotice] = useState('')

  const loadOperators = useCallback(async (targetPage: number) => {
    setIsLoading(true)
    try {
      const result = await getOperators(targetPage, PAGE_SIZE)
      setOperators(result.content)
      setTotalPages(result.totalPages)
      setTotalElements(result.totalElements)
      setLoadError('')
    } catch (error) {
      setLoadError(toErrorMessage(error, '관리자 목록을 불러오지 못했습니다.'))
    } finally {
      setIsLoading(false)
    }
  }, [])

  useEffect(() => {
    // 렌더 직후 동기 setState 를 피하려고 다른 페이지처럼 타이머로 미룬다.
    const timer = window.setTimeout(() => void loadOperators(page), 0)
    return () => window.clearTimeout(timer)
  }, [loadOperators, page])

  useEffect(() => {
    getControlCenters()
      .then(setCenters)
      .catch(() => {
        // 센터 목록 조회 실패는 목록 화면을 막지 않는다. 폼에서 다시 안내한다.
      })
  }, [])

  const openCreate = () => {
    setForm(emptyForm)
    setFormError('')
    setIsCreateOpen(true)
  }

  const openEdit = (operator: OperatorSummary) => {
    setForm({
      loginId: operator.loginId,
      password: '',
      displayName: operator.displayName,
      controlCenterId: operator.controlCenterId ? String(operator.controlCenterId) : '',
      membershipRole: operator.membershipRole ?? MEMBERSHIP_ROLE_OPTIONS[0].value,
    })
    setFormError('')
    setEditTarget(operator)
  }

  const closeForm = () => {
    setIsCreateOpen(false)
    setEditTarget(null)
  }

  const submitForm = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (isSaving) return

    const displayName = form.displayName.trim()
    const controlCenterId = Number(form.controlCenterId)

    if (!displayName) {
      setFormError('표시 이름을 입력해주세요.')
      return
    }
    if (!form.controlCenterId || !Number.isInteger(controlCenterId)) {
      setFormError('소속 관제센터를 선택해주세요.')
      return
    }

    if (isCreateOpen) {
      const loginId = form.loginId.trim()
      if (!loginId) {
        setFormError('로그인 아이디를 입력해주세요.')
        return
      }
      if (form.password.length < 10) {
        setFormError('초기 비밀번호는 10자 이상이어야 합니다.')
        return
      }
    }

    setIsSaving(true)
    setFormError('')
    try {
      if (isCreateOpen) {
        const created = await createOperator({
          loginId: form.loginId.trim(),
          password: form.password,
          displayName,
          controlCenterId,
          membershipRole: form.membershipRole,
        })
        setNotice(`${created.displayName} 계정을 생성했습니다.`)
      } else if (editTarget) {
        const updated = await updateOperator(editTarget.id, {
          displayName,
          controlCenterId,
          membershipRole: form.membershipRole,
        })
        setNotice(`${updated.displayName} 정보를 수정했습니다.`)
      }
      closeForm()
      await loadOperators(page)
    } catch (error) {
      setFormError(toErrorMessage(error, '저장하지 못했습니다.'))
    } finally {
      setIsSaving(false)
    }
  }

  const toggleStatus = async (operator: OperatorSummary) => {
    if (statusChangingId !== null) return

    const nextStatus = operator.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE'
    setStatusChangingId(operator.id)
    try {
      const updated = await changeOperatorStatus(operator.id, nextStatus)
      setOperators((current) => current.map((item) => (item.id === updated.id ? updated : item)))
      setNotice(`${updated.displayName} 계정을 ${OPERATOR_STATUS_LABELS[updated.status]} 상태로 변경했습니다.`)
    } catch (error) {
      setNotice(toErrorMessage(error, '상태를 변경하지 못했습니다.'))
    } finally {
      setStatusChangingId(null)
    }
  }

  const isFormOpen = isCreateOpen || editTarget !== null

  return (
    <section className="page-stack">
      {notice && (
        <aside className="monitoring-notice" role="status" aria-live="polite">
          <span>{notice}</span>
          <button type="button" className="text-button" onClick={() => setNotice('')}>확인</button>
        </aside>
      )}

      <AdminSection
        title={`관제 담당자 계정 (${totalElements}명)`}
        action={
          <button type="button" className="primary-button" onClick={openCreate}>
            + 담당자 생성
          </button>
        }
      >
        {isLoading ? (
          <p role="status">관리자 목록을 불러오는 중입니다...</p>
        ) : loadError ? (
          <p className="empty-state" role="alert">{loadError}</p>
        ) : operators.length === 0 ? (
          <p className="empty-state">등록된 관리자 계정이 없습니다.</p>
        ) : (
          <div className="table-wrap">
            <table className="admin-table">
              <thead>
                <tr>
                  <th scope="col">이름</th>
                  <th scope="col">아이디</th>
                  <th scope="col">역할</th>
                  <th scope="col">소속 관제센터</th>
                  <th scope="col">직책</th>
                  <th scope="col">상태</th>
                  <th scope="col">최근 로그인</th>
                  <th scope="col">관리</th>
                </tr>
              </thead>
              <tbody>
                {operators.map((operator) => (
                  <tr key={operator.id}>
                    <td>{operator.displayName}</td>
                    <td>{operator.loginId}</td>
                    <td>{OPERATOR_ROLE_LABELS[operator.role] ?? operator.role}</td>
                    <td>{operator.controlCenterName ?? '미지정'}</td>
                    <td>{membershipRoleLabel(operator.membershipRole)}</td>
                    <td>
                      <StatusBadge tone={operator.status === 'ACTIVE' ? 'success' : 'danger'}>
                        {OPERATOR_STATUS_LABELS[operator.status]}
                      </StatusBadge>
                    </td>
                    <td>{operator.lastLoginAt ? formatAdminDate(operator.lastLoginAt) : '기록 없음'}</td>
                    <td>
                      <div className="operator-row-actions">
                        <button type="button" className="secondary-button" onClick={() => openEdit(operator)}>
                          수정
                        </button>
                        {/* SUPER_ADMIN 본인 계정을 정지하면 콘솔에서 잠기므로 담당자만 상태를 바꾼다. */}
                        {operator.role === 'OPERATOR' && (
                          <button
                            type="button"
                            className="secondary-button"
                            disabled={statusChangingId !== null}
                            onClick={() => void toggleStatus(operator)}
                          >
                            {statusChangingId === operator.id
                              ? '변경 중...'
                              : operator.status === 'ACTIVE' ? '정지' : '활성화'}
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {totalPages > 1 && (
          <nav className="operator-pagination" aria-label="관리자 목록 페이지">
            <button
              type="button"
              className="secondary-button"
              disabled={page === 0}
              onClick={() => setPage((current) => Math.max(0, current - 1))}
            >
              이전
            </button>
            <span>{page + 1} / {totalPages}</span>
            <button
              type="button"
              className="secondary-button"
              disabled={page >= totalPages - 1}
              onClick={() => setPage((current) => current + 1)}
            >
              다음
            </button>
          </nav>
        )}
      </AdminSection>

      <AdminModal
        open={isFormOpen}
        title={isCreateOpen ? '담당자 계정 생성' : `${editTarget?.displayName ?? ''} 정보 수정`}
        onClose={closeForm}
      >
        <form className="operator-form" onSubmit={(event) => void submitForm(event)}>
          {isCreateOpen && (
            <>
              <label>
                로그인 아이디
                <input
                  type="text"
                  value={form.loginId}
                  maxLength={100}
                  onChange={(event) => setForm((current) => ({ ...current, loginId: event.target.value }))}
                  placeholder="예: op23a"
                />
              </label>
              <label>
                초기 비밀번호 (10자 이상)
                <input
                  type="password"
                  value={form.password}
                  minLength={10}
                  maxLength={100}
                  autoComplete="new-password"
                  onChange={(event) => setForm((current) => ({ ...current, password: event.target.value }))}
                />
              </label>
            </>
          )}
          <label>
            표시 이름
            <input
              type="text"
              value={form.displayName}
              maxLength={255}
              onChange={(event) => setForm((current) => ({ ...current, displayName: event.target.value }))}
              placeholder="예: 강남구 담당자 A"
            />
          </label>
          <label>
            소속 관제센터
            <select
              value={form.controlCenterId}
              onChange={(event) => setForm((current) => ({ ...current, controlCenterId: event.target.value }))}
            >
              <option value="" disabled>관제센터 선택</option>
              {centers.map((center) => (
                <option key={center.id} value={center.id}>{center.name}</option>
              ))}
            </select>
          </label>
          <label>
            직책
            <select
              value={form.membershipRole}
              onChange={(event) => setForm((current) => ({ ...current, membershipRole: event.target.value }))}
            >
              {MEMBERSHIP_ROLE_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
            </select>
          </label>

          {formError && <p className="login-error" role="alert">{formError}</p>}

          <div className="operator-form__actions">
            <button type="button" className="secondary-button" onClick={closeForm}>취소</button>
            <button type="submit" className="primary-button" disabled={isSaving}>
              {isSaving ? '저장 중...' : isCreateOpen ? '생성' : '저장'}
            </button>
          </div>
        </form>
      </AdminModal>
    </section>
  )
}
