import { useState, type FormEvent } from 'react'
import { AdminSection } from '../../components/admin/AdminSection'
import { useAuth } from '../../auth/useAuth'
import { changeMyPassword } from './settingsApi'

const PASSWORD_MIN_LENGTH = 10

/**
 * 계정 설정. 이전의 시스템 상태 요약은 전부 목 데이터였고 대응하는 백엔드 API 가 없어
 * 제거했다. 지금은 실제로 연동 가능한 비밀번호 변경(PATCH /admin/me/password)만 제공한다.
 */
export function SystemSettingsPage() {
  const { user, adminName } = useAuth()
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [newPasswordConfirm, setNewPasswordConfirm] = useState('')
  const [errorMessage, setErrorMessage] = useState('')
  const [successMessage, setSuccessMessage] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (isSubmitting) return

    setSuccessMessage('')

    if (!currentPassword) {
      setErrorMessage('현재 비밀번호를 입력해주세요.')
      return
    }
    if (newPassword.length < PASSWORD_MIN_LENGTH) {
      setErrorMessage(`새 비밀번호는 ${PASSWORD_MIN_LENGTH}자 이상이어야 합니다.`)
      return
    }
    if (newPassword !== newPasswordConfirm) {
      setErrorMessage('새 비밀번호가 서로 일치하지 않습니다.')
      return
    }
    if (newPassword === currentPassword) {
      setErrorMessage('현재 비밀번호와 다른 비밀번호를 사용해주세요.')
      return
    }

    setIsSubmitting(true)
    setErrorMessage('')
    try {
      await changeMyPassword(currentPassword, newPassword)
      setCurrentPassword('')
      setNewPassword('')
      setNewPasswordConfirm('')
      setSuccessMessage('비밀번호를 변경했습니다.')
    } catch (error) {
      setErrorMessage(
        error instanceof Error && error.message
          ? error.message
          : '비밀번호를 변경하지 못했습니다. 현재 비밀번호를 확인해주세요.',
      )
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <section className="page-stack">
      <AdminSection title="내 계정">
        <dl className="report-summary">
          <div><dt>이름</dt><dd>{adminName ?? '정보 없음'}</dd></div>
          <div><dt>역할</dt><dd>{user?.role === 'SUPER_ADMIN' ? '최고 관리자' : '관제 담당자'}</dd></div>
        </dl>
      </AdminSection>

      <AdminSection title="비밀번호 변경">
        <form className="operator-form settings-password-form" onSubmit={(event) => void submit(event)}>
          <label>
            현재 비밀번호
            <input
              type="password"
              value={currentPassword}
              autoComplete="current-password"
              onChange={(event) => setCurrentPassword(event.target.value)}
            />
          </label>
          <label>
            새 비밀번호 ({PASSWORD_MIN_LENGTH}자 이상)
            <input
              type="password"
              value={newPassword}
              minLength={PASSWORD_MIN_LENGTH}
              maxLength={100}
              autoComplete="new-password"
              onChange={(event) => setNewPassword(event.target.value)}
            />
          </label>
          <label>
            새 비밀번호 확인
            <input
              type="password"
              value={newPasswordConfirm}
              minLength={PASSWORD_MIN_LENGTH}
              maxLength={100}
              autoComplete="new-password"
              onChange={(event) => setNewPasswordConfirm(event.target.value)}
            />
          </label>

          {errorMessage && <p className="login-error" role="alert">{errorMessage}</p>}
          {successMessage && <p className="settings-success" role="status">{successMessage}</p>}

          <div className="operator-form__actions">
            <button type="submit" className="primary-button" disabled={isSubmitting}>
              {isSubmitting ? '변경 중...' : '비밀번호 변경'}
            </button>
          </div>
        </form>
      </AdminSection>
    </section>
  )
}
