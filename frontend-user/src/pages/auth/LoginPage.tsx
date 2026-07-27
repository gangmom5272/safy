import { type FormEvent, useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router'
import { SafyLogo } from '../../components/SafyLogo'
import { useAuthStore } from '../../stores/authStore'
import './login.css'

const HOME_INTRO_KEY = 'safy-home-intro-completed'

function VisibilityIcon({ visible }: { visible: boolean }) {
  return visible
    ? <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M2 12s3.5-6 10-6 10 6 10 6-3.5 6-10 6S2 12 2 12Z"/><circle cx="12" cy="12" r="3"/></svg>
    : <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m3 3 18 18M10.6 10.7a2 2 0 0 0 2.7 2.7M9.9 5.2A10.8 10.8 0 0 1 12 5c6.5 0 10 7 10 7a18 18 0 0 1-2.1 3M6.6 6.6C3.7 8.4 2 12 2 12s3.5 7 10 7a10 10 0 0 0 4.2-.9"/></svg>
}

export function LoginPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const signupState = location.state as { email?: string; signupCompleted?: boolean } | null
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated)
  const login = useAuthStore((state) => state.login)
  const [identifier, setIdentifier] = useState(signupState?.email ?? '')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [submitted, setSubmitted] = useState(false)

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setSubmitted(true)
    if (!identifier.trim() || !password) return

    sessionStorage.removeItem(HOME_INTRO_KEY)
    login()
    navigate('/app', { replace: true })
  }

  if (isAuthenticated) return <Navigate to="/app" replace />

  return (
    <main className="login-page">
      <div className="login-page__inner">
        <header className="login-brand">
          <SafyLogo className="login-brand__logo" />
          <h1>SAFY</h1>
          <strong>Safe Area For You</strong>
          <p>실시간으로 확인하는 우리 주변의 안전</p>
        </header>

        <form className="login-form" noValidate onSubmit={handleSubmit}>
          {signupState?.signupCompleted && <p className="login-success" role="status">회원가입이 완료되었습니다.<br />로그인해주세요.</p>}
          <div className="login-field">
            <label htmlFor="identifier">아이디 또는 이메일</label>
            <input id="identifier" name="identifier" autoComplete="username" value={identifier} onChange={(event) => setIdentifier(event.target.value)} aria-invalid={submitted && !identifier.trim()} aria-describedby={submitted && !identifier.trim() ? 'identifier-error' : undefined} />
            {submitted && !identifier.trim() && <p id="identifier-error" className="login-field__error">아이디 또는 이메일을 입력해주세요.</p>}
          </div>

          <div className="login-field">
            <label htmlFor="password">비밀번호</label>
            <div className="login-password">
              <input id="password" name="password" type={showPassword ? 'text' : 'password'} autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)} aria-invalid={submitted && !password} aria-describedby={submitted && !password ? 'password-error' : undefined} />
              <button type="button" onClick={() => setShowPassword((current) => !current)} aria-label={showPassword ? '비밀번호 숨기기' : '비밀번호 보기'}><VisibilityIcon visible={showPassword} /></button>
            </div>
            {submitted && !password && <p id="password-error" className="login-field__error">비밀번호를 입력해주세요.</p>}
          </div>

          <div className="login-options">
            <label><input type="checkbox" /> 로그인 상태 유지</label>
            <button type="button">아이디·비밀번호 찾기</button>
          </div>

          <button className="login-submit" type="submit">로그인</button>
        </form>

        <div className="login-signup">
          <span>아직 회원이 아니신가요?</span>
          <button type="button" onClick={() => navigate('/signup')}>회원가입</button>
        </div>
      </div>
    </main>
  )
}
