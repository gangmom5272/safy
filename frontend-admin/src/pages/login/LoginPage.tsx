import { useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate } from 'react-router'
import { useAuth } from '../../auth/useAuth'
import { ROUTE_PATHS } from '../../router/paths'

const TEST_ADMIN_EMAIL = 'admin@safy.kr'
const TEST_ADMIN_PASSWORD = 'safy1234'

export function LoginPage() {
  const navigate = useNavigate()
  const { login } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errorMessage, setErrorMessage] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()

    if (isSubmitting) {
      return
    }

    setIsSubmitting(true)
    setErrorMessage('')

    if (!email.trim()) {
      setErrorMessage('이메일을 입력해주세요.')
      setIsSubmitting(false)
      return
    }

    if (!password) {
      setErrorMessage('비밀번호를 입력해주세요.')
      setIsSubmitting(false)
      return
    }

    const isLoggedIn = login({ email: email.trim(), password })

    if (!isLoggedIn) {
      setErrorMessage('이메일 또는 비밀번호가 올바르지 않습니다.')
      setIsSubmitting(false)
      return
    }

    setPassword('')
    setErrorMessage('')
    navigate(ROUTE_PATHS.DASHBOARD, { replace: true })
  }

  return (
    <main className="login-page">
      <section className="login-card" aria-labelledby="login-title">
        <div className="login-brand">
          <strong>SAFY</strong>
          <span>Mock login for admin routing validation</span>
        </div>
        <div>
          <h1 id="login-title">관리자 로그인</h1>
          <p>SAFY 관리자 전용 페이지입니다. 현재 로그인은 테스트용 Mock 흐름입니다.</p>
        </div>

        <form className="login-form" onSubmit={handleSubmit}>
          <label htmlFor="admin-email">이메일</label>
          <input
            id="admin-email"
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            autoComplete="username"
            placeholder="admin@safy.kr"
          />

          <label htmlFor="admin-password">비밀번호</label>
          <input
            id="admin-password"
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            autoComplete="current-password"
            placeholder="비밀번호"
          />

          {errorMessage && (
            <p className="login-error" role="alert" aria-live="polite">
              {errorMessage}
            </p>
          )}

          <button type="submit" className="primary-button login-submit" disabled={isSubmitting}>
            로그인
          </button>
        </form>

        <aside className="mock-account" aria-label="Mock 테스트 계정">
          <strong>Mock 테스트 계정</strong>
          <span>이 이메일과 비밀번호는 화면 흐름 검증 전용이며 실제 인증 정보가 아닙니다.</span>
          <dl>
            <div>
              <dt>이메일</dt>
              <dd>{TEST_ADMIN_EMAIL}</dd>
            </div>
            <div>
              <dt>비밀번호</dt>
              <dd>{TEST_ADMIN_PASSWORD}</dd>
            </div>
          </dl>
        </aside>
      </section>
    </main>
  )
}
