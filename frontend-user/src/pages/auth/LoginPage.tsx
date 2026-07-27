import { Navigate } from 'react-router'
import { SafyLogo } from '../../components/SafyLogo'
import { useAuthStore } from '../../stores/authStore'
import './login.css'

type SocialProvider = 'kakao' | 'google'

function beginSocialLogin(provider: SocialProvider) {
  const baseUrl = import.meta.env.VITE_API_BASE_URL.replace(/\/$/, '')
  window.location.assign(`${baseUrl}/auth/social/${provider}/login`)
}

export function LoginPage() {
  const status = useAuthStore((state) => state.status)
  const sessionMessage = useAuthStore((state) => state.sessionMessage)

  if (status === 'authenticated') return <Navigate to="/app" replace />

  return (
    <main className="login-page">
      <div className="login-page__inner">
        <header className="login-brand">
          <SafyLogo className="login-brand__logo" />
          <h1>SAFY</h1>
          <strong>Safe Area For You</strong>
          <p>소셜 계정으로 안전하게 로그인하세요.</p>
        </header>

        {sessionMessage && <p className="login-field__error" role="alert">{sessionMessage}</p>}
        <div className="login-form" aria-label="소셜 로그인">
          <button className="login-submit" type="button" onClick={() => beginSocialLogin('kakao')}>
            Kakao로 계속하기
          </button>
          <button className="login-submit" type="button" onClick={() => beginSocialLogin('google')}>
            Google로 계속하기
          </button>
        </div>
      </div>
    </main>
  )
}
