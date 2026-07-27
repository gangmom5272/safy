import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router'
import { useAuthStore } from '../../stores/authStore'
import { exchangeSocialCode, getCurrentUser } from './authApi'
import { canUseCitizenApp } from './authSession'

const loginRequests = new Map<string, ReturnType<typeof completeOAuthLogin>>()

async function completeOAuthLogin(code: string) {
  await exchangeSocialCode(code)
  return getCurrentUser()
}

function getLoginRequest(code: string) {
  const existing = loginRequests.get(code)
  if (existing) return existing

  const request = completeOAuthLogin(code)
  loginRequests.set(code, request)
  return request
}

export function OAuthCallbackPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const authenticate = useAuthStore((state) => state.authenticate)
  const code = searchParams.get('code')
  const [errorMessage, setErrorMessage] = useState(() => code ? '' : '로그인 인증 코드가 없습니다.')

  useEffect(() => {
    if (!code) return
    const loginCode = code

    let active = true
    async function completeLogin() {
      try {
        const user = await getLoginRequest(loginCode)
        if (!canUseCitizenApp(user)) throw new Error('Citizen role required')
        if (!active) return
        authenticate(user)
        navigate('/app', { replace: true })
      } catch (error) {
        if (active) setErrorMessage(error instanceof Error ? error.message : '로그인을 완료하지 못했습니다.')
      }
    }

    void completeLogin()
    return () => { active = false }
  }, [authenticate, code, navigate])

  return (
    <main className="login-page">
      <div className="login-page__inner">
        <p role={errorMessage ? 'alert' : 'status'}>
          {errorMessage || '로그인을 완료하고 있습니다...'}
        </p>
        {errorMessage && <button type="button" onClick={() => navigate('/login', { replace: true })}>로그인으로 돌아가기</button>}
      </div>
    </main>
  )
}
