import { useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate } from 'react-router'
import { useAuth } from '../../auth/useAuth'

export function LoginPage() {
  const navigate = useNavigate()
  const { login, sessionMessage } = useAuth()
  const [loginId, setLoginId] = useState('')
  const [password, setPassword] = useState('')
  const [errorMessage, setErrorMessage] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()

    if (isSubmitting) {
      return
    }

    setErrorMessage('')

    if (!loginId.trim()) {
      setErrorMessage('아이디를 입력해주세요.')
      return
    }

    if (!password) {
      setErrorMessage('비밀번호를 입력해주세요.')
      return
    }

    setIsSubmitting(true)

    try {
      await login({ loginId: loginId.trim(), password })
      setPassword('')
      // 역할별 첫 화면 분기는 루트의 RoleHome 이 담당한다.
      navigate('/', { replace: true })
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '로그인하지 못했습니다.')
      setIsSubmitting(false)
    }
  }

  return (
    <main className="login-page">
      <section className="login-card" aria-labelledby="login-title">
        <div className="login-brand">
          <strong>SAFY</strong>
          <span>SAFY Admin Console</span>
        </div>
        <div>
          <h1 id="login-title">관리자 로그인</h1>
          <p>SAFY 관리자 전용 페이지입니다.</p>
        </div>

        {sessionMessage && (
          <p className="login-error" role="alert" aria-live="polite">
            {sessionMessage}
          </p>
        )}

        <form className="login-form" onSubmit={handleSubmit}>
          <label htmlFor="admin-login-id">아이디</label>
          <input
            id="admin-login-id"
            type="text"
            value={loginId}
            onChange={(event) => setLoginId(event.target.value)}
            autoComplete="username"
            placeholder="operator01"
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
            {isSubmitting ? '로그인 중...' : '로그인'}
          </button>
        </form>
      </section>
    </main>
  )
}
