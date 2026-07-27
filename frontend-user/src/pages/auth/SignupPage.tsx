import { type FormEvent, useEffect, useRef, useState } from 'react'
import { Navigate, useNavigate } from 'react-router'
import { useAuthStore } from '../../stores/authStore'
import './signup.css'

const MOCK_VERIFICATION_CODE = '123456'
const VERIFICATION_SECONDS = 180
const namePattern = /^[가-힣a-zA-Z]+(?:[ '-][가-힣a-zA-Z]+)*$/
const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const phonePattern = /^010-\d{4}-\d{4}$/
const passwordPattern = /^(?=.*[A-Za-z])(?=.*\d)(?=.*[^A-Za-z\d\s])\S{8,}$/

function EyeIcon({ visible }: { visible: boolean }) {
  return visible
    ? <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M2 12s3.5-6 10-6 10 6 10 6-3.5 6-10 6S2 12 2 12Z"/><circle cx="12" cy="12" r="3"/></svg>
    : <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m3 3 18 18M10.6 10.7a2 2 0 0 0 2.7 2.7M9.9 5.2A10.8 10.8 0 0 1 12 5c6.5 0 10 7 10 7a18 18 0 0 1-2.1 3M6.6 6.6C3.7 8.4 2 12 2 12s3.5 7 10 7a10 10 0 0 0 4.2-.9"/></svg>
}

function formatPhone(value: string) {
  const digits = value.replace(/\D/g, '').slice(0, 11)
  if (digits.length <= 3) return digits
  if (digits.length <= 7) return `${digits.slice(0, 3)}-${digits.slice(3)}`
  return `${digits.slice(0, 3)}-${digits.slice(3, 7)}-${digits.slice(7)}`
}

function formatTimer(seconds: number) {
  return `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`
}

export function SignupPage() {
  const navigate = useNavigate()
  const submitTimer = useRef<number | null>(null)
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated)
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [phone, setPhone] = useState('')
  const [code, setCode] = useState('')
  const [verificationRequested, setVerificationRequested] = useState(false)
  const [phoneVerified, setPhoneVerified] = useState(false)
  const [secondsLeft, setSecondsLeft] = useState(0)
  const [codeError, setCodeError] = useState('')
  const [password, setPassword] = useState('')
  const [passwordConfirm, setPasswordConfirm] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [showPasswordConfirm, setShowPasswordConfirm] = useState(false)
  const [termsAccepted, setTermsAccepted] = useState(false)
  const [privacyAccepted, setPrivacyAccepted] = useState(false)
  const [touched, setTouched] = useState<Record<string, boolean>>({})
  const [submitting, setSubmitting] = useState(false)

  const validName = namePattern.test(name.trim())
  const validEmail = emailPattern.test(email.trim())
  const validPhone = phonePattern.test(phone)
  const validPassword = passwordPattern.test(password)
  const passwordsMatch = password.length > 0 && password === passwordConfirm
  const canSubmit = validName && validEmail && validPhone && phoneVerified && validPassword && passwordsMatch && termsAccepted && privacyAccepted && !submitting

  useEffect(() => {
    if (!verificationRequested || phoneVerified || secondsLeft <= 0) return
    const timer = window.setInterval(() => setSecondsLeft((current) => Math.max(0, current - 1)), 1000)
    return () => window.clearInterval(timer)
  }, [verificationRequested, phoneVerified, secondsLeft])

  useEffect(() => () => {
    if (submitTimer.current !== null) window.clearTimeout(submitTimer.current)
  }, [])

  if (isAuthenticated) return <Navigate to="/app" replace />

  const requestVerification = () => {
    if (!validPhone) { setTouched((current) => ({ ...current, phone: true })); return }
    setVerificationRequested(true)
    setPhoneVerified(false)
    setCode('')
    setCodeError('')
    setSecondsLeft(VERIFICATION_SECONDS)
  }

  const verifyCode = () => {
    if (secondsLeft <= 0) { setCodeError('인증 시간이 만료되었습니다. 재전송해주세요.'); return }
    if (code !== MOCK_VERIFICATION_CODE) { setCodeError('인증번호가 일치하지 않습니다.'); return }
    setPhoneVerified(true)
    setCodeError('')
  }

  const handlePhoneChange = (value: string) => {
    setPhone(formatPhone(value))
    setVerificationRequested(false)
    setPhoneVerified(false)
    setSecondsLeft(0)
    setCode('')
    setCodeError('')
  }

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setTouched({ name: true, email: true, phone: true, password: true, passwordConfirm: true })
    if (!canSubmit) return
    setSubmitting(true)
    submitTimer.current = window.setTimeout(() => navigate('/login', { replace: true, state: { email: email.trim(), signupCompleted: true } }), 400)
  }

  return (
    <main className="signup-page">
      <div className="signup-page__inner">
        <header className="signup-header"><button type="button" aria-label="로그인으로 돌아가기" onClick={() => navigate('/login', { replace: true })}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m15 5-7 7 7 7"/><path d="M8 12h12"/></svg></button><h1>회원가입</h1><span /></header>
        <form className="signup-form" noValidate onSubmit={submit}>
          <label className="signup-field"><span>이름</span><input value={name} placeholder="실명을 입력해주세요" autoComplete="name" onBlur={() => setTouched((v) => ({ ...v, name: true }))} onChange={(e) => setName(e.target.value)} />{touched.name && !name.trim() && <small>이름을 입력해주세요.</small>}{touched.name && name.trim() && !validName && <small>한글 또는 영문으로 실명을 입력해주세요.</small>}</label>
          <label className="signup-field"><span>이메일</span><input type="email" value={email} placeholder="이메일을 입력해주세요" autoComplete="email" onBlur={() => setTouched((v) => ({ ...v, email: true }))} onChange={(e) => setEmail(e.target.value)} />{touched.email && !validEmail && <small>올바른 이메일을 입력해주세요.</small>}</label>
          <div className="signup-field"><label htmlFor="signup-phone">전화번호</label><div className="signup-inline"><input id="signup-phone" inputMode="numeric" value={phone} placeholder="휴대전화 번호를 입력해주세요" autoComplete="tel" onBlur={() => setTouched((v) => ({ ...v, phone: true }))} onChange={(e) => handlePhoneChange(e.target.value)} /><button type="button" disabled={!validPhone} onClick={requestVerification}>{verificationRequested ? '재전송' : '인증 요청'}</button></div>{touched.phone && !validPhone && <small>올바른 휴대전화 번호를 입력해주세요.</small>}</div>
          {verificationRequested && <div className="signup-field"><label htmlFor="signup-code">전화번호 인증번호</label><div className="signup-inline signup-code"><input id="signup-code" inputMode="numeric" maxLength={6} value={code} placeholder="인증번호를 입력해주세요" disabled={phoneVerified} onChange={(e) => { setCode(e.target.value.replace(/\D/g, '').slice(0, 6)); setCodeError('') }} /><time>{formatTimer(secondsLeft)}</time><button type="button" disabled={code.length !== 6 || secondsLeft <= 0 || phoneVerified} onClick={verifyCode}>확인</button></div>{phoneVerified && <p>전화번호 인증이 완료되었습니다.</p>}{codeError && <small>{codeError}</small>}</div>}
          <div className="signup-field"><label htmlFor="signup-password">비밀번호</label><div className="signup-password"><input id="signup-password" type={showPassword ? 'text' : 'password'} value={password} placeholder="비밀번호를 입력해주세요" autoComplete="new-password" onBlur={() => setTouched((v) => ({ ...v, password: true }))} onChange={(e) => setPassword(e.target.value.trim())} /><button type="button" aria-label={showPassword ? '비밀번호 숨기기' : '비밀번호 보기'} onClick={() => setShowPassword((v) => !v)}><EyeIcon visible={showPassword} /></button></div><em>영문, 숫자, 특수문자를 포함한 8자 이상</em>{touched.password && !validPassword && <small>영문, 숫자, 특수문자를 포함해 8자 이상 입력해주세요.</small>}</div>
          <div className="signup-field"><label htmlFor="signup-password-confirm">비밀번호 확인</label><div className="signup-password"><input id="signup-password-confirm" type={showPasswordConfirm ? 'text' : 'password'} value={passwordConfirm} placeholder="비밀번호를 다시 입력해주세요" autoComplete="new-password" onBlur={() => setTouched((v) => ({ ...v, passwordConfirm: true }))} onChange={(e) => setPasswordConfirm(e.target.value.trim())} /><button type="button" aria-label={showPasswordConfirm ? '비밀번호 확인 숨기기' : '비밀번호 확인 보기'} onClick={() => setShowPasswordConfirm((v) => !v)}><EyeIcon visible={showPasswordConfirm} /></button></div>{touched.passwordConfirm && !passwordsMatch && <small>비밀번호가 일치하지 않습니다.</small>}</div>
          <fieldset className="signup-terms"><legend>약관 동의</legend><label><input type="checkbox" checked={termsAccepted && privacyAccepted} onChange={(e) => { setTermsAccepted(e.target.checked); setPrivacyAccepted(e.target.checked) }} /><strong>전체 동의</strong></label><label><input type="checkbox" checked={termsAccepted} onChange={(e) => setTermsAccepted(e.target.checked)} /><button type="button">[필수] 이용약관 동의</button></label><label><input type="checkbox" checked={privacyAccepted} onChange={(e) => setPrivacyAccepted(e.target.checked)} /><button type="button">[필수] 개인정보 수집 및 이용 동의</button></label></fieldset>
          <button className="signup-submit" type="submit" disabled={!canSubmit}>{submitting ? '가입 처리 중...' : '회원가입'}</button>
        </form>
      </div>
    </main>
  )
}
