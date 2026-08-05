import { useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { PageHeader } from '../../../components/common/PageHeader'
import { cancelMyReport, getMe, getMyReport, getMyReports, getNotificationSettings, getReportSummary, logout, PROFILE_IMAGE_CONTENT_TYPES, resetProfileImage, updateMyProfile, updateNotificationSettings, uploadProfileImage } from './mypageApi'
import { mockInquiries, mockNotificationSettings, mockReports, mockSummary, mockUser, USE_MYPAGE_MOCK_FALLBACK } from './mypageMock'
import type { Inquiry, MyPageView, MyReport, Notice, NotificationSettingsData, ReportSummary, UserProfile } from './mypageTypes'
import { useAuthStore } from '../../../stores/authStore'
import { Faqs, Guide, Inquiries, InquiryDetail, InquiryForm, NoticeDetail, Notices, ReportDetail, Reports, SectionTitle, Support } from './MyPageFeatures'
import { subscribeToRealtimeSignals } from '../../../api/realtime'
import './mypage.css'
import './mypageRestored.css'
import './mypageMenuContrast.css'
import './mypageMainRedesign.css'

type Initial = { view?: MyPageView; reportId?: number }
type ReportNavigationState = {
  from?: 'notifications' | 'my-report-list'
  returnTo?: string
  notificationFilter?: string
}

export function MyPage() { return <MyPageApp /> }
export function MyPageApp({ view: initialView = 'MAIN', reportId }: Initial) {
  const navigate = useNavigate(); const location = useLocation(); const mounted = useRef(true)
  const clearAuthentication = useAuthStore((state) => state.clearAuthentication)
  const [view, setView] = useState<MyPageView>(initialView); const [history, setHistory] = useState<MyPageView[]>([])
  const [user, setUser] = useState<UserProfile>(mockUser); const [summary, setSummary] = useState<ReportSummary>(mockSummary)
  const [reports, setReports] = useState<MyReport[]>([]); const [detailReport, setDetailReport] = useState<MyReport | null>(null); const [inquiries, setInquiries] = useState<Inquiry[]>(mockInquiries)
  const [notificationSettings, setNotificationSettings] = useState<NotificationSettingsData>(mockNotificationSettings)
  const selectedReportId = reportId ?? null; const [selectedNotice, setSelectedNotice] = useState<Notice | null>(null); const [selectedInquiry, setSelectedInquiry] = useState<Inquiry | null>(null)
  const [loading, setLoading] = useState(true); const [notificationUsingMock, setNotificationUsingMock] = useState(false); const [message, setMessage] = useState('')
  // 상세 응답은 목록보다 첨부 정보가 많다. 목록은 대표 이미지 한 장만 주고 상세는
  // images 배열 전체를 준다. 상세를 목록 state 에 섞어 두면, 나중에 끝난 목록 조회가
  // setReports 로 통째로 덮어써 첨부가 한 장으로 줄어든다(요청 순서에 따라 재현됨).
  // 그래서 상세는 목록과 분리해 따로 들고, 목록 값은 상세가 오기 전까지만 쓴다.
  const selectedReport = detailReport && detailReport.id === selectedReportId
    ? detailReport
    : reports.find((item) => item.id === selectedReportId) ?? null
  const go = (next: MyPageView) => { setHistory((items) => [...items, view]); setView(next); setMessage(''); window.scrollTo({ top: 0, behavior: 'smooth' }) }
  const back = () => { const previous = history.at(-1) ?? 'MAIN'; setHistory((items) => items.slice(0, -1)); setView(previous); setMessage('') }

  useEffect(() => { mounted.current = true; const timer = window.setTimeout(async () => { const results = await Promise.allSettled([getMe(), getReportSummary(), getMyReports(), getNotificationSettings()]); if (!mounted.current) return; const [u, s, r, n] = results; if (u.status === 'fulfilled') setUser(u.value); if (s.status === 'fulfilled') setSummary(s.value); if (r.status === 'fulfilled') setReports(r.value); if (n.status === 'fulfilled') setNotificationSettings(n.value); if (results.some((result) => result.status === 'rejected') && USE_MYPAGE_MOCK_FALLBACK) { console.error('Mypage API failed; using mock fallback', results); if (u.status === 'rejected') setUser(mockUser); if (s.status === 'rejected') setSummary(mockSummary); if (r.status === 'rejected') setReports(mockReports); if (n.status === 'rejected') { setNotificationSettings(mockNotificationSettings); setNotificationUsingMock(true) } } setLoading(false) }, 0); return () => { mounted.current = false; window.clearTimeout(timer) } }, [])

  useEffect(() => subscribeToRealtimeSignals((signal) => {
    if (signal.kind === 'event'
      && signal.event.eventType !== 'REPORT_CLOSED'
      && signal.event.eventType !== 'REPORT_REJECTED') return

    void Promise.all([getReportSummary(), getMyReports()]).then(([nextSummary, nextReports]) => {
      if (!mounted.current) return
      setSummary(nextSummary)
      setReports(nextReports)
    }).catch(() => undefined)

    if (signal.kind === 'event' && selectedReportId != null && selectedReportId === signal.event.reportId) {
      void getMyReport(selectedReportId).then((report) => {
        if (!mounted.current) return
        setDetailReport(report)
        setReports((current) => [...current.filter((item) => item.id !== report.id), report])
      }).catch(() => undefined)
    }
  }), [selectedReportId])

  useEffect(() => {
    if (!selectedReportId) return
    let active = true
    void getMyReport(selectedReportId).then((report) => {
      if (!active) return
      setDetailReport(report)
      setReports((current) => [...current.filter((item) => item.id !== report.id), report])
    }).catch((error: unknown) => {
      if (active) setMessage(error instanceof Error ? error.message : '제보 상세를 불러오지 못했습니다.')
    })
    return () => { active = false }
  }, [selectedReportId])

  const openReport = (id: number) => navigate(`/app/mypage/reports/${id}`, {
    state: { from: 'my-report-list', returnTo: '/app/mypage/reports' } satisfies ReportNavigationState,
  })
  const handleReportDetailBack = () => {
    const navigationState = location.state as ReportNavigationState | null
    const returnTo = navigationState?.returnTo === '/app/notifications'
      ? '/app/notifications'
      : '/app/mypage/reports'
    const state = returnTo === '/app/notifications' && navigationState?.notificationFilter
      ? { notificationFilter: navigationState.notificationFilter }
      : undefined
    navigate(returnTo, { replace: true, state })
  }
  // 취소는 서버가 204 만 주므로 취소 결과는 상세·목록을 다시 받아 확인한다. 취소 요청과
  // 재조회를 분리해야 재조회만 실패했을 때 취소 실패로 잘못 안내하지 않는다.
  const handleCancelReport = async (reportId: number) => {
    if (!window.confirm('이 제보를 취소할까요?')) return

    try {
      await cancelMyReport(reportId)
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '제보를 취소하지 못했습니다.')
      return
    }

    setMessage('')
    const [detailResult, listResult] = await Promise.allSettled([getMyReport(reportId), getMyReports()])
    if (!mounted.current) return

    // 목록을 먼저 반영하고 상세를 마지막에 덮어써야 상세의 첨부 목록이 목록 값에 밀리지 않는다.
    if (listResult.status === 'fulfilled') setReports(listResult.value)
    if (detailResult.status === 'fulfilled') {
      const report = detailResult.value
      setDetailReport(report)
      setReports((current) => [...current.filter((item) => item.id !== report.id), report])
      return
    }

    // 재조회가 실패해도 취소 자체는 이미 끝났다. 취소된 상태만 반영해 두고 따로 안내한다.
    setDetailReport((current) => current && current.id === reportId ? { ...current, status: 'CANCELLED' } : current)
    setReports((current) => current.map((item) => item.id === reportId ? { ...item, status: 'CANCELLED' } : item))
    setMessage('제보는 취소되었지만 최신 정보를 불러오지 못했습니다. 잠시 후 다시 확인해주세요.')
  }
  const handleLogout = () => {
    if (!window.confirm('로그아웃할까요?')) return
    clearAuthentication()
    sessionStorage.removeItem('safy-home-intro-completed')
    sessionStorage.removeItem('safy-mock-authenticated')
    void logout().catch((error: unknown) => console.error('Logout failed', error))
    navigate('/login', { replace: true })
  }
  if (loading) return <div className="mypage-shell"><div className="mypage-loading">마이페이지를 불러오는 중...</div></div>

  return <div className="mypage-shell">{view === 'MAIN' && <PageHeader title="마이페이지" onBack={() => navigate('/app', { replace: true })} />}{message && <p className="mypage-message" role="status">{message}</p>}
    {view === 'MAIN' && <Main user={user} summary={summary} reports={reports} onGo={(next) => { if (next === 'REPORTS') navigate('/app/mypage/reports'); else go(next) }} onLogout={handleLogout} />}
    {view === 'SETTINGS' && <Settings onBack={back} onGo={go} />}
    {view === 'PROFILE_EDIT' && <ProfileEdit user={user} onBack={back} onUserChanged={setUser} onSaved={(nextUser) => { setUser(nextUser); setMessage('프로필이 수정되었습니다.'); setHistory([]); setView('MAIN') }} />}
    {view === 'ACCOUNT' && <Account user={user} onBack={back} onLogout={handleLogout} />}
    {view === 'PRIVACY' && <Privacy onBack={back} />}
    {view === 'NOTIFICATION_SETTINGS' && <NotificationPreferences settings={notificationSettings} usingMock={notificationUsingMock} onBack={back} onChange={setNotificationSettings} />}
    {view === 'REPORTS' && <Reports reports={reports} onBack={() => navigate('/app/mypage', { replace: true })} onOpen={openReport} />}
    {view === 'REPORT_DETAIL' && selectedReport && <ReportDetail report={selectedReport} onBack={handleReportDetailBack} onCancel={() => handleCancelReport(selectedReport.id)} />}
    {view === 'SUPPORT' && <Support onBack={back} onGo={go} />}{view === 'FAQ' && <Faqs onBack={back} />}{view === 'NOTICES' && <Notices onBack={back} onOpen={(notice) => { setSelectedNotice(notice); go('NOTICE_DETAIL') }} />}{view === 'NOTICE_DETAIL' && selectedNotice && <NoticeDetail notice={selectedNotice} onBack={back} />}
    {view === 'INQUIRY_FORM' && <InquiryForm onBack={back} onSubmit={(item) => { setInquiries((items) => [item, ...items]); setMessage('문의가 등록되었습니다.'); setView('SUPPORT') }} />}{view === 'INQUIRIES' && <Inquiries items={inquiries} onBack={back} onOpen={(item) => { setSelectedInquiry(item); go('INQUIRY_DETAIL') }} />}{view === 'INQUIRY_DETAIL' && selectedInquiry && <InquiryDetail item={selectedInquiry} onBack={back} />}{view === 'GUIDE' && <Guide onBack={back} />}
  </div>
}

function Main({ user, summary, reports, onGo, onLogout }: { user: UserProfile; summary: ReportSummary; reports: MyReport[]; onGo: (view: MyPageView) => void; onLogout: () => void }) {
  const menus: [MenuIconName, string, MyPageView][] = [['settings','설정','SETTINGS'],['support','고객센터','SUPPORT'],['guide','이용안내','GUIDE']]
  // active 는 아직 종결되지 않은 제보(접수 대기 중 + 처리중)다. 목록을 이미 받아왔으면
  // 목록에서 직접 세고, 아직 없으면 요약 API 값을 쓴다.
  const reportCounts = reports.length ? { active: reports.filter((item) => item.status === 'RECEIVED' || item.status === 'IN_PROGRESS').length, closed: reports.filter((item) => item.status === 'CLOSED').length, rejected: reports.filter((item) => item.status === 'REJECTED').length } : { active: summary.receivedCount + summary.inProgressCount, closed: summary.closedCount, rejected: summary.rejectedCount }
  return <><section className="profile-card"><div className="profile-avatar-wrap"><div className="avatar">{user.profileImageUrl ? <img src={user.profileImageUrl} alt={`${user.nickname} 프로필`} /> : user.nickname.slice(0, 1)}</div><button type="button" className="profile-edit-button" aria-label="프로필 수정" onClick={() => onGo('PROFILE_EDIT')}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m4 20 4.5-1 10-10a2.1 2.1 0 0 0-3-3l-10 10Z"/><path d="m14 7 3 3"/></svg></button></div><div className="profile-details"><h2>{user.nickname}</h2>{user.email && <p>{user.email}</p>}</div></section><section className="mypage-card my-report-summary"><SectionTitle title="내 제보 관리" action="전체 보기" onAction={() => onGo('REPORTS')} /><div className="report-status-grid"><span><b>{reportCounts.active}</b>진행중</span><span><b>{reportCounts.closed}</b>처리 완료</span><span><b>{reportCounts.rejected}</b>반려됨</span></div></section><section className="mypage-menu">{menus.map(([icon,label,target]) => <button key={target} type="button" onClick={() => onGo(target)}><span className="mypage-menu-icon"><MenuIcon name={icon} /></span><b>{label}</b><em aria-hidden="true"><MenuIcon name="chevron" /></em></button>)}</section><button type="button" className="logout-button" onClick={onLogout}>로그아웃</button></>

}

type MenuIconName = 'settings' | 'support' | 'guide'

function MenuIcon({ name }: { name: MenuIconName | 'chevron' }) {
  const paths = {
    settings: <><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1-2.8 2.8-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.6v.2h-4V21a1.7 1.7 0 0 0-1-1.6 1.7 1.7 0 0 0-1.9.3l-.1.1L4.2 17l.1-.1a1.7 1.7 0 0 0 .3-1.9A1.7 1.7 0 0 0 3 14H2.8v-4H3a1.7 1.7 0 0 0 1.6-1 1.7 1.7 0 0 0-.3-1.9L4.2 7 7 4.2l.1.1a1.7 1.7 0 0 0 1.9.3 1.7 1.7 0 0 0 1-1.6v-.2h4V3a1.7 1.7 0 0 0 1 1.6 1.7 1.7 0 0 0 1.9-.3l.1-.1L19.8 7l-.1.1a1.7 1.7 0 0 0-.3 1.9 1.7 1.7 0 0 0 1.6 1h.2v4H21a1.7 1.7 0 0 0-1.6 1Z"/></>,
    support: <><path d="M4 13a8 8 0 0 1 16 0"/><path d="M4 13v5h4v-6H4M20 13v5h-4v-6h4M16 20c-1 1-2 1-4 1"/></>,
    guide: <><circle cx="12" cy="12" r="9"/><path d="M12 11v6M12 7h.01"/></>,
    chevron: <path d="m9 5 7 7-7 7"/>,
  }
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name]}</svg>
}

function Settings({ onBack, onGo }: { onBack: () => void; onGo: (view: MyPageView) => void }) { const items: [SettingsIconName,string,string,MyPageView][] = [['account','계정 관리','계정 정보와 보안을 관리해요.','ACCOUNT'],['privacy','개인·보안','개인정보와 계정 보안 안내를 확인해요.','PRIVACY'],['notification','알림','유형별 알림 수신 여부를 설정해요.','NOTIFICATION_SETTINGS']]; return <><PageHeader title="설정" onBack={onBack} className="settings-header" /><section className="settings-cards">{items.map(([icon,title,description,target]) => <button type="button" key={target} onClick={() => onGo(target)}><span><SettingsIcon name={icon} /></span><span><strong>{title}</strong><small>{description}</small></span><b><SettingsIcon name="chevron" /></b></button>)}</section></> }
// 표시 이름은 백엔드가 @NotBlank @Size(max = 255) 로만 검증한다. 255자는 화면이 깨지므로
// 입력 자체를 20자로 제한하고, 여기서는 빈 값만 막는다.
const PROFILE_NAME_MAX_LENGTH = 20

// 이름은 저장 버튼으로 PATCH /users/me 에 보낸다. 사진은 MinIO 로 직접 업로드하는
// 별도 흐름이라 선택·되돌리기 즉시 반영하고, 성공 후 서버 값을 다시 받아 화면에 쓴다.
// 미리보기를 만들어 두면 저장 전까지 서버 상태와 다른 사진이 보여 이전 QA 와 같은 혼동이 된다.
// 이메일은 소셜 로그인 값이라 수정 API 가 없어 읽기 전용이다.
function ProfileEdit({ user, onBack, onSaved, onUserChanged }: { user: UserProfile; onBack: () => void; onSaved: (user: UserProfile) => void; onUserChanged: (user: UserProfile) => void }) {
  const [name, setName] = useState(user.nickname)
  const [nameTouched, setNameTouched] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [imageBusy, setImageBusy] = useState(false)
  const [imageError, setImageError] = useState('')
  const trimmedName = name.trim()
  const validName = trimmedName.length > 0
  const nameChanged = trimmedName !== user.nickname
  const canSave = !saving && nameChanged && validName

  // 업로드와 되돌리기가 같은 순서를 쓴다. 성공하면 getMe 로 새 조회 URL 을 받아 아바타에
  // 반영한다. presigned URL 은 만료되므로 프론트가 URL 을 만들어 쓸 수는 없다.
  const applyImageChange = async (change: () => Promise<unknown>, failureMessage: string) => {
    setImageBusy(true)
    setImageError('')
    try {
      await change()
      onUserChanged(await getMe())
    } catch (requestError) {
      console.error(failureMessage, requestError)
      setImageError(requestError instanceof Error ? requestError.message : failureMessage)
    } finally {
      setImageBusy(false)
    }
  }

  const selectImage = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    // 같은 파일을 다시 고를 수 있게 비워 둔다. 업로드가 실패했을 때 재시도가 막히지 않는다.
    event.target.value = ''
    if (!file) return
    if (!PROFILE_IMAGE_CONTENT_TYPES.includes(file.type)) { setImageError('jpg, jpeg, png, webp 파일만 등록할 수 있습니다.'); return }
    void applyImageChange(() => uploadProfileImage(file), '프로필 사진을 등록하지 못했습니다.')
  }

  const resetImage = () => {
    if (!window.confirm('프로필 사진을 기본 사진으로 되돌릴까요?')) return
    void applyImageChange(() => resetProfileImage(), '프로필 사진을 되돌리지 못했습니다.')
  }

  const submit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!canSave) return
    setSaving(true)
    setError('')

    try {
      const saved = await updateMyProfile(trimmedName)
      // 저장 성공 시 상위에서 MAIN 으로 전환되며 이 컴포넌트가 사라진다.
      onSaved({ ...user, ...saved })
    } catch (requestError) {
      console.error('Profile update failed', requestError)
      setError('프로필을 저장하지 못했습니다. 잠시 후 다시 시도해주세요.')
      setSaving(false)
    }
  }

  return <><PageHeader title="프로필 수정" onBack={onBack} className="profile-edit-header" /><form className="mypage-form account-form profile-edit-form" onSubmit={(event) => void submit(event)}><div className="profile-edit-avatar"><div className="avatar">{user.profileImageUrl ? <img src={user.profileImageUrl} alt={`${user.nickname} 프로필`} /> : user.nickname.slice(0, 1)}</div><div className="profile-photo-actions"><label className={imageBusy ? 'profile-photo-button disabled' : 'profile-photo-button'}>{imageBusy ? '적용 중...' : '사진 변경'}<input type="file" accept=".jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp" disabled={imageBusy} onChange={selectImage} /></label><button type="button" className="profile-photo-reset" disabled={imageBusy} onClick={resetImage}>기본 사진으로</button></div>{imageError && <small className="profile-field-error">{imageError}</small>}</div>{error && <p className="profile-edit-error" role="alert">{error}</p>}<div className="profile-edit-field"><label htmlFor="profile-name">이름</label><input id="profile-name" value={name} maxLength={PROFILE_NAME_MAX_LENGTH} placeholder="이름을 입력해주세요." onBlur={() => setNameTouched(true)} onChange={(event) => setName(event.target.value)} />{nameTouched && !validName && <small className="profile-field-error">이름을 입력해주세요.</small>}</div>{user.email && <div className="profile-edit-field"><label htmlFor="profile-email">이메일</label><input id="profile-email" value={user.email} readOnly /></div>}<button type="submit" disabled={!canSave}>{saving ? '저장 중...' : '저장'}</button></form></>
}
function Account({ user, onBack, onLogout }: { user: UserProfile; onBack: () => void; onLogout: () => void }) { const [showWithdrawal, setShowWithdrawal] = useState(false); return <><PageHeader title="계정 관리" onBack={onBack} className="account-header" /><section className="account-menu"><button type="button"><span>계정 정보</span>{user.email && <small>{user.email}</small>}<b aria-hidden="true">›</b></button><button type="button" onClick={onLogout}><span>로그아웃</span><b aria-hidden="true">›</b></button></section><button type="button" className="account-withdrawal" onClick={() => setShowWithdrawal(true)}>회원 탈퇴</button>{showWithdrawal && <div className="account-modal-backdrop" role="presentation"><section className="account-modal" role="dialog" aria-modal="true" aria-labelledby="withdrawal-title"><h2 id="withdrawal-title">회원 탈퇴</h2><p>회원 탈퇴 시 계정 정보가 삭제될 수 있습니다. 계속하시겠어요?</p><div><button type="button" onClick={() => setShowWithdrawal(false)}>취소</button><button type="button" className="danger" onClick={() => setShowWithdrawal(false)}>탈퇴하기</button></div></section></div>}</> }

type SettingsIconName = 'account' | 'privacy' | 'notification' | 'chevron'

function SettingsIcon({ name }: { name: SettingsIconName }) {
  const paths = {
    account: <><circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/></>,
    privacy: <><path d="M12 3 5 6v5c0 4.6 2.8 8.2 7 10 4.2-1.8 7-5.4 7-10V6Z"/><path d="m9 12 2 2 4-4"/></>,
    notification: <><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9"/><path d="M10 21h4"/></>,
    chevron: <path d="m9 5 7 7-7 7"/>,
  }
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name]}</svg>
}
function Privacy({ onBack }: { onBack: () => void }) { const items = ['개인정보 처리방침','위치정보 이용 안내','데이터 및 개인정보 관리','회원 탈퇴 및 계정 보안 안내']; return <><PageHeader title="개인·보안" onBack={onBack} /><section className="privacy-list">{items.map((item) => <article key={item}><span>▣</span><div><strong>{item}</strong><small>읽기 전용 안내 · 준비 중</small></div></article>)}</section></> }
function NotificationPreferences({ settings, usingMock, onBack, onChange }: { settings: NotificationSettingsData; usingMock: boolean; onBack: () => void; onChange: (settings: NotificationSettingsData) => void }) { const [saving, setSaving] = useState(false); const [error, setError] = useState(''); const options: [keyof NotificationSettingsData,string,string][] = [['enabled','전체 알림','모든 앱 알림을 허용합니다.'],['reportEnabled','제보 관련 알림','접수와 처리 상태를 알려드려요.'],['safetyEnabled','안전 정보 알림','주변 위험과 혼잡 정보를 알려드려요.'],['locationRequestEnabled','주변 현장 제보 요청','최근 위치 주변의 현장 제보 요청을 받아요.'],['systemEnabled','시스템 알림','공지와 점검 소식을 알려드려요.']]; const toggle = async (key: keyof NotificationSettingsData) => { if (saving) return; const next = { ...settings, [key]: !settings[key] }; onChange(next); setError(''); if (usingMock) return; setSaving(true); try { const saved = await updateNotificationSettings(next); onChange(saved) } catch (requestError) { console.error('Notification settings update failed', requestError); onChange(settings); setError('알림 설정을 저장하지 못했습니다.') } finally { setSaving(false) } }; return <><PageHeader title="알림" onBack={onBack} /><aside className="settings-banner"><span>🔔</span><div><strong>중요 알림을 놓치지 마세요!</strong><p>필요한 알림을 켜두면 더 빠르게 정보를 받을 수 있어요.</p></div></aside>{error && <p className="profile-edit-error" role="alert">{error}</p>}<section className="notification-setting-list">{options.map(([key,label,description]) => <div key={key}><label htmlFor={`mypage-${key}`}><strong>{label}</strong><small>{description}</small></label><button id={`mypage-${key}`} type="button" role="switch" aria-checked={settings[key]} className={settings[key] ? 'setting-toggle on' : 'setting-toggle'} onClick={() => void toggle(key)} disabled={saving || (key !== 'enabled' && !settings.enabled)}><span /></button></div>)}</section></> }
