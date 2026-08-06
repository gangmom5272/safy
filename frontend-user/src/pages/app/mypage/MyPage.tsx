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
  const [privacyTopic, setPrivacyTopic] = useState<PrivacyIconName | null>(null)
  const [loading, setLoading] = useState(true); const [notificationUsingMock, setNotificationUsingMock] = useState(false); const [message, setMessage] = useState('')
  // 상세 응답은 목록보다 첨부 정보가 많다. 목록은 대표 이미지 한 장만 주고 상세는
  // images 배열 전체를 준다. 상세를 목록 state 에 섞어 두면, 나중에 끝난 목록 조회가
  // setReports 로 통째로 덮어써 첨부가 한 장으로 줄어든다(요청 순서에 따라 재현됨).
  // 그래서 상세는 목록과 분리해 따로 들고, 목록 값은 상세가 오기 전까지만 쓴다.
  const selectedReport = detailReport && detailReport.id === selectedReportId
    ? detailReport
    : reports.find((item) => item.id === selectedReportId) ?? null
  const go = (next: MyPageView) => { setHistory((items) => [...items, view]); setView(next); setMessage(''); window.scrollTo({ top: 0, behavior: 'smooth' }) }
  const back = () => {
    if (history.length === 0) {
      if (view !== 'MAIN') navigate('/app/mypage', { replace: true })
      return
    }
    const previous = history.at(-1) as MyPageView
    setHistory((items) => items.slice(0, -1))
    setView(previous)
    setMessage('')
  }

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
    {view === 'PRIVACY' && <Privacy onBack={back} onOpen={(topic) => { setPrivacyTopic(topic); go('PRIVACY_DETAIL') }} />}
    {view === 'PRIVACY_DETAIL' && privacyTopic && <PrivacyDetail topic={privacyTopic} onBack={back} />}
    {view === 'NOTIFICATION_SETTINGS' && <NotificationPreferences settings={notificationSettings} usingMock={notificationUsingMock} onBack={back} onChange={setNotificationSettings} />}
    {view === 'REPORTS' && <Reports reports={reports} onBack={() => navigate('/app/mypage', { replace: true })} onOpen={openReport} />}
    {view === 'REPORT_DETAIL' && selectedReport && <ReportDetail report={selectedReport} onBack={handleReportDetailBack} onCancel={() => handleCancelReport(selectedReport.id)} />}
    {view === 'SUPPORT' && <Support onBack={back} onGo={go} />}{view === 'FAQ' && <Faqs onBack={back} />}{view === 'NOTICES' && <Notices onBack={back} onOpen={(notice) => { setSelectedNotice(notice); go('NOTICE_DETAIL') }} />}{view === 'NOTICE_DETAIL' && selectedNotice && <NoticeDetail notice={selectedNotice} onBack={back} />}
    {view === 'INQUIRY_FORM' && <InquiryForm onBack={back} onSubmit={(item) => { setInquiries((items) => [item, ...items]); setMessage('문의가 등록되었습니다.'); setView('SUPPORT') }} />}{view === 'INQUIRIES' && <Inquiries items={inquiries} onBack={back} onOpen={(item) => { setSelectedInquiry(item); go('INQUIRY_DETAIL') }} />}{view === 'INQUIRY_DETAIL' && selectedInquiry && <InquiryDetail item={selectedInquiry} onBack={back} />}{view === 'GUIDE' && <Guide onBack={back} />}
  </div>
}

function Main({ user, summary, reports, onGo, onLogout }: { user: UserProfile; summary: ReportSummary; reports: MyReport[]; onGo: (view: MyPageView) => void; onLogout: () => void }) {
  const menus: [MenuIconName, string, MyPageView][] = [['support','고객센터','SUPPORT'],['guide','이용안내','GUIDE']]
  // active 는 아직 종결되지 않은 제보(접수 대기 중 + 처리중)다. 목록을 이미 받아왔으면
  // 목록에서 직접 세고, 아직 없으면 요약 API 값을 쓴다.
  const reportCounts = reports.length ? { active: reports.filter((item) => item.status === 'RECEIVED' || item.status === 'IN_PROGRESS').length, closed: reports.filter((item) => item.status === 'CLOSED').length, rejected: reports.filter((item) => item.status === 'REJECTED').length } : { active: summary.receivedCount + summary.inProgressCount, closed: summary.closedCount, rejected: summary.rejectedCount }
  return <><section className="profile-card"><div className="profile-avatar-wrap"><div className="avatar">{user.profileImageUrl ? <img src={user.profileImageUrl} alt={`${user.nickname} 프로필`} /> : user.nickname.slice(0, 1)}</div><button type="button" className="profile-edit-button" aria-label="프로필 수정" onClick={() => onGo('PROFILE_EDIT')}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m4 20 4.5-1 10-10a2.1 2.1 0 0 0-3-3l-10 10Z"/><path d="m14 7 3 3"/></svg></button></div><div className="profile-details"><h2>{user.nickname}</h2>{user.email && <p>{user.email}</p>}</div></section><section className="mypage-card my-report-summary"><SectionTitle title="내 제보 관리" action="전체 보기" onAction={() => onGo('REPORTS')} /><div className="report-status-grid"><span><b>{reportCounts.active}</b>진행중</span><span><b>{reportCounts.closed}</b>처리 완료</span><span><b>{reportCounts.rejected}</b>반려됨</span></div></section><section className="mypage-menu">{menus.map(([icon,label,target]) => <button key={target} type="button" onClick={() => onGo(target)}><span className="mypage-menu-icon"><MenuIcon name={icon} /></span><b>{label}</b><em aria-hidden="true"><MenuIcon name="chevron" /></em></button>)}</section><button type="button" className="logout-button" onClick={onLogout}>로그아웃</button></>

}

type MenuIconName = 'support' | 'guide'

function MenuIcon({ name }: { name: MenuIconName | 'chevron' }) {
  const paths = {
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
type PrivacyIconName = 'policy' | 'location' | 'data' | 'security'
function PrivacyIcon({ name }: { name: PrivacyIconName }) {
  const paths = {
    policy: <><path d="M8 3h6l4 4v13a1 1 0 0 1-1 1H8a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1Z"/><path d="M14 3v4h4"/><path d="M9 12h6M9 16h6"/></>,
    location: <><path d="M12 21s7-6.5 7-12a7 7 0 0 0-14 0c0 5.5 7 12 7 12Z"/><circle cx="12" cy="9" r="2.5"/></>,
    data: <><ellipse cx="12" cy="6" rx="8" ry="3"/><path d="M4 6v6c0 1.7 3.6 3 8 3s8-1.3 8-3V6"/><path d="M4 12v6c0 1.7 3.6 3 8 3s8-1.3 8-3v-6"/></>,
    security: <><rect x="5" y="11" width="14" height="9" rx="2"/><path d="M8 11V7a4 4 0 0 1 8 0v4"/></>,
  }
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name]}</svg>
}
type PrivacyBlock =
  | { h: string }
  | { p: string }
  | { ul: string[] }
  | { ol: string[] }
  | { small: string }
  | { table: { headers: string[]; rows: string[][] } }

// 실제 서비스 약관은 아니다. SSAFY 프로젝트 데모용 초안이다. 날짜·보관기간·위탁업체·
// 담당자 연락처는 2026-08-05 기준 임시값으로 채워뒀으니 실제 운영 전 팀 확정값으로
// 교체해야 한다. 남아있는 [설정 > ...] [고객센터] 표기는 빈칸이 아니라 실제 메뉴
// 경로를 가리키는 표기다. 코드로 존재가 확인되지 않는 기능(데이터 다운로드, 제보
// 내용 수정, 낯선 기기 로그인 알림)은 문구에서 뺐다 — mypageApi.ts 에 대응 API가 없다.
const PRIVACY_CONTENT: Record<PrivacyIconName, PrivacyBlock[]> = {
  policy: [
    { p: 'SAFY(이하 "서비스")는 「개인정보 보호법」 등 관련 법령을 준수하며, 이용자의 개인정보를 보호하고 관련 고충을 신속하게 처리할 수 있도록 다음과 같이 개인정보 처리방침을 수립·공개합니다.' },
    { h: '제1조 (수집하는 개인정보 항목)' },
    { table: { headers: ['구분', '수집 항목', '수집 방법'], rows: [
      ['필수', '이메일, 비밀번호(암호화 저장), 닉네임', '회원가입 시 직접 입력'],
      ['필수', '제보 시 촬영 영상·이미지, GPS 위치정보', '제보 기능 이용 시'],
      ['자동 수집', '접속 IP, 접속 로그, 기기 정보, 서비스 이용 기록', '서비스 이용 중 자동 생성'],
      ['선택', '프로필 이미지', '회원 설정 시 직접 입력'],
    ] } },
    { h: '제2조 (개인정보의 수집 및 이용 목적)' },
    { ol: [
      '회원 식별 및 서비스 부정이용 방지',
      '위험 제보 접수·검증 및 관리자 승인 처리',
      '실시간 영상 스트리밍(WebRTC)을 통한 현장 상황 확인',
      'AI 영상 분석을 통한 구역별 위험도 산출 및 시각화',
      '긴급재난문자 연동 정보와 시민 제보 정보의 매칭',
      '서비스 개선을 위한 통계 분석(비식별 처리 후)',
    ] },
    { h: '제3조 (개인정보의 보유 및 이용 기간)' },
    { ul: [
      '회원 탈퇴 시까지 보유하며, 탈퇴 처리 시 파기함을 원칙으로 합니다.',
      '제보 영상·이미지는 관련 법령 또는 내부 정책에 따라 승인일로부터 90일 별도 보관 후 파기합니다.',
      '관계 법령에 따라 보존이 필요한 경우 해당 법령이 정한 기간 동안 보관합니다. (서비스 이용 관련 기록: 3개월·통신비밀보호법 / 계약·청약철회 관련 기록: 5년·전자상거래법, 해당 시)',
    ] },
    { h: '제4조 (개인정보의 제3자 제공)' },
    { p: '서비스는 이용자의 개인정보를 원칙적으로 외부에 제공하지 않습니다. 다만 다음의 경우는 예외로 합니다.' },
    { ol: [
      '이용자가 사전에 동의한 경우',
      '법령의 규정에 의거하거나, 수사 목적으로 법령에 정해진 절차와 방법에 따라 수사기관의 요구가 있는 경우',
      '재난 상황 대응을 위해 관계 기관(소방·경찰 등)에 제보 정보를 공유하는 경우(긴급 상황에 한함)',
    ] },
    { h: '제5조 (개인정보 처리 위탁)' },
    { table: { headers: ['수탁업체', '위탁업무 내용'], rows: [
      ['AWS', '서버 운영 및 데이터 저장'],
      ['MinIO', '제보 영상·이미지 저장'],
    ] } },
    { h: '제6조 (이용자의 권리와 행사 방법)' },
    { p: '이용자는 언제든지 다음의 권리를 행사할 수 있습니다.' },
    { ul: ['개인정보 열람 요구', '오류 등이 있을 경우 정정 요구', '삭제 요구', '처리 정지 요구'] },
    { p: '권리 행사는 마이페이지 내 [설정 > 개인·보안] 메뉴 또는 [고객센터]를 통해 요청할 수 있으며, 서비스는 지체 없이 조치합니다.' },
    { h: '제7조 (개인정보의 파기 절차 및 방법)' },
    { ul: ['전자적 파일 형태: 복구 불가능한 방법으로 영구 삭제', '종이 문서: 해당 없음 (서비스는 전자적으로만 정보를 수집)'] },
    { h: '제8조 (개인정보 보호책임자)' },
    { table: { headers: ['구분', '내용'], rows: [
      ['성명', 'SAFY 운영팀'],
      ['직책', '개인정보 보호책임자'],
      ['연락처', 'safy.privacy@example.com'],
    ] } },
    { h: '제9조 (고지의 의무)' },
    { p: '본 방침은 시행일로부터 적용되며, 내용 추가·삭제·수정이 있는 경우 개정 최소 7일 전부터 서비스 내 공지사항을 통해 고지합니다.' },
    { small: '시행일자: 2026.08.01' },
  ],
  location: [
    { p: 'SAFY는 「위치정보의 보호 및 이용 등에 관한 법률」에 따라 이용자의 위치정보를 안전하게 관리하고 있습니다.' },
    { h: '제1조 (수집하는 위치정보)' },
    { ul: ['제보 등록 시 기기의 GPS 기반 실시간 위치정보(위도·경도)', '상황 지도에서 사용자가 확인 중인 조회 위치(선택적, 지도 이동 시)'] },
    { h: '제2조 (위치정보의 이용 목적)' },
    { ol: [
      '제보된 위험 상황의 정확한 지점 파악',
      '상황 지도상 구역별 위험도·혼잡도 시각화',
      '관리자의 현장 위치 기반 대응 우선순위 판단',
      '인접 사용자에게 주변 위험 상황 안내(추후 기능 예정 시)',
    ] },
    { h: '제3조 (위치정보의 보유 및 이용 기간)' },
    { ul: [
      '실시간 위치정보는 제보 처리 완료 후 30일 이내 파기합니다.',
      '위치정보를 포함한 제보 기록은 통계·안전 정책 수립 목적으로 비식별화하여 보관될 수 있습니다.',
    ] },
    { h: '제4조 (위치정보의 제3자 제공)' },
    { p: '이용자의 사전 동의 없이 위치정보를 제3자에게 제공하지 않습니다. 다만 긴급 구조를 위해 관계 법령에 따라 요청이 있는 경우는 예외로 합니다.' },
    { h: '제5조 (위치정보 이용·제공에 대한 동의 철회)' },
    { p: '이용자는 언제든지 [설정 > 위치정보 이용 동의] 메뉴에서 동의를 철회할 수 있습니다. 단, 동의 철회 시 실시간 제보 기능 이용이 제한될 수 있습니다.' },
    { h: '제6조 (개인위치정보관리책임자)' },
    { table: { headers: ['구분', '내용'], rows: [
      ['성명', 'SAFY 운영팀'],
      ['연락처', 'safy.privacy@example.com'],
    ] } },
    { small: '시행일자: 2026.08.01' },
  ],
  data: [
    { p: '이 메뉴에서는 회원님이 SAFY에 저장된 본인의 데이터를 직접 확인하고 관리할 수 있습니다.' },
    { h: '내 데이터 열람' },
    { ul: [
      '계정 정보: 이메일, 닉네임, 가입일 확인',
      '제보 이력: 지금까지 등록한 제보 목록, 처리 상태(진행중/처리완료/반려)',
      '위치정보 제공 기록: 최근 제보에 사용된 위치정보 이력',
    ] },
    { h: '내 데이터 다운로드' },
    { p: '회원님의 계정 정보와 제보 이력을 파일(JSON/CSV)로 내려받는 기능을 제공할 예정입니다. 현재 버전에는 다운로드 기능이 아직 구현되어 있지 않습니다. 필요 시 [고객센터]로 문의해 주시면 안내드립니다.' },
    { h: '데이터 수정' },
    { ul: [
      '닉네임, 프로필 이미지: 마이페이지에서 즉시 수정 가능',
      '제보 취소: 관리자 검토 전 상태에 한해 취소 가능, 검토 진입 후에는 취소 불가(허위 제보 방지를 위한 정책). 등록된 제보 내용 자체의 수정은 현재 지원하지 않습니다.',
    ] },
    { h: '데이터 삭제 요청' },
    { p: '계정을 유지한 채 특정 제보 기록만 삭제하고 싶은 경우 [고객센터]로 삭제를 요청할 수 있습니다. 단, 이미 관리자가 승인해 대응 절차가 진행 중인 제보는 안전상의 이유로 즉시 삭제가 제한될 수 있습니다.' },
    { h: '자동화된 의사결정에 대한 안내' },
    { p: 'SAFY의 AI 영상분석은 구역별 위험도 산출을 보조하는 목적으로만 사용되며, 최종 대응 여부는 관리자의 검토를 거쳐 결정됩니다. AI 분석 결과만으로 자동 처리되는 개인정보 관련 결정은 없습니다.' },
  ],
  security: [
    { h: '회원 탈퇴 절차' },
    { ol: [
      '[마이페이지 > 설정 > 회원 탈퇴] 메뉴 진입',
      '탈퇴 시 유의사항 확인',
      '비밀번호 재입력을 통한 본인 확인',
      '탈퇴 사유 선택(선택 사항)',
      '최종 확인 후 탈퇴 완료',
    ] },
    { h: '탈퇴 시 처리되는 사항' },
    { ul: [
      '계정 정보(이메일, 닉네임, 비밀번호)는 탈퇴 처리 시 삭제됩니다. (현재 데모 버전에서는 탈퇴 확인 후 실제 데이터 삭제 처리가 연동되어 있지 않습니다.)',
      '등록한 제보 기록은 비식별화 후 보관됩니다. 이미 처리 완료된 제보(대응 이력 등)는 통계·기록 목적상 작성자 정보만 비식별화하여 보관될 수 있습니다.',
      '탈퇴 후 7일간 동일 이메일로 재가입이 제한됩니다. (부정 이용 방지)',
    ] },
    { h: '탈퇴 전 확인해 주세요' },
    { ul: [
      '진행 중인 제보가 있다면 처리 완료 후 탈퇴를 권장합니다.',
      '탈퇴 후에는 제보 이력 및 데이터를 복구할 수 없습니다.',
      '데이터 다운로드 기능은 현재 준비 중입니다. 필요한 정보가 있다면 탈퇴 전 [고객센터]로 문의해 주세요.',
    ] },
    { h: '계정 보안 안내' },
    { ul: [
      '비밀번호는 타인에게 공유하지 마시고, 다른 서비스와 동일한 비밀번호 사용을 피해주세요.',
      '계정 도용이 의심되는 경우 즉시 [고객센터]로 문의해 주세요.',
      '비밀번호 변경은 [마이페이지 > 설정 > 비밀번호 변경]에서 언제든 가능합니다.',
    ] },
  ],
}

const PRIVACY_TITLES: Record<PrivacyIconName, string> = {
  policy: '개인정보 처리방침',
  location: '위치정보 이용 안내',
  data: '데이터 및 개인정보 관리',
  security: '회원 탈퇴 및 계정 보안 안내',
}

function PrivacyBlockView({ block }: { block: PrivacyBlock }) {
  if ('h' in block) return <h3>{block.h}</h3>
  if ('p' in block) return <p>{block.p}</p>
  if ('small' in block) return <small>{block.small}</small>
  if ('ul' in block) return <ul>{block.ul.map((line) => <li key={line}>{line}</li>)}</ul>
  if ('ol' in block) return <ol>{block.ol.map((line) => <li key={line}>{line}</li>)}</ol>
  const { headers, rows } = block.table
  return <div className="privacy-detail-table"><table><thead><tr>{headers.map((header) => <th key={header}>{header}</th>)}</tr></thead><tbody>{rows.map((row, rowIndex) => <tr key={rowIndex}>{row.map((cell, cellIndex) => <td key={cellIndex}>{cell}</td>)}</tr>)}</tbody></table></div>
}

function PrivacyDetail({ topic, onBack }: { topic: PrivacyIconName; onBack: () => void }) {
  return <><PageHeader title={PRIVACY_TITLES[topic]} onBack={onBack} /><section className="text-detail privacy-detail">{PRIVACY_CONTENT[topic].map((block, index) => <PrivacyBlockView key={index} block={block} />)}</section></>
}

function Privacy({ onBack, onOpen }: { onBack: () => void; onOpen: (topic: PrivacyIconName) => void }) {
  const items: [PrivacyIconName, string][] = [
    ['policy', '개인정보 처리방침'],
    ['location', '위치정보 이용 안내'],
    ['data', '데이터 및 개인정보 관리'],
    ['security', '회원 탈퇴 및 계정 보안 안내'],
  ]
  return <><PageHeader title="개인·보안" onBack={onBack} /><section className="privacy-list">{items.map(([icon, item]) => <button type="button" key={item} onClick={() => onOpen(icon)}><span><PrivacyIcon name={icon} /></span><div><strong>{item}</strong><small>자세히 보기</small></div><b aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="m9 5 7 7-7 7"/></svg></b></button>)}</section></>
}
function NotificationPreferences({ settings, usingMock, onBack, onChange }: { settings: NotificationSettingsData; usingMock: boolean; onBack: () => void; onChange: (settings: NotificationSettingsData) => void }) { const [saving, setSaving] = useState(false); const [error, setError] = useState(''); const options: [keyof NotificationSettingsData,string,string][] = [['enabled','전체 알림','모든 앱 알림을 허용합니다.'],['reportEnabled','제보 관련 알림','접수와 처리 상태를 알려드려요.'],['safetyEnabled','안전 정보 알림','주변 위험과 혼잡 정보를 알려드려요.'],['locationRequestEnabled','주변 현장 제보 요청','최근 위치 주변의 현장 제보 요청을 받아요.'],['systemEnabled','시스템 알림','공지와 점검 소식을 알려드려요.']]; const toggle = async (key: keyof NotificationSettingsData) => { if (saving) return; const next = { ...settings, [key]: !settings[key] }; onChange(next); setError(''); if (usingMock) return; setSaving(true); try { const saved = await updateNotificationSettings(next); onChange(saved) } catch (requestError) { console.error('Notification settings update failed', requestError); onChange(settings); setError('알림 설정을 저장하지 못했습니다.') } finally { setSaving(false) } }; return <><PageHeader title="알림" onBack={onBack} /><aside className="settings-banner"><span>🔔</span><div><strong>중요 알림을 놓치지 마세요!</strong><p>필요한 알림을 켜두면 더 빠르게 정보를 받을 수 있어요.</p></div></aside>{error && <p className="profile-edit-error" role="alert">{error}</p>}<section className="notification-setting-list">{options.map(([key,label,description]) => <div key={key}><label htmlFor={`mypage-${key}`}><strong>{label}</strong><small>{description}</small></label><button id={`mypage-${key}`} type="button" role="switch" aria-checked={settings[key]} className={settings[key] ? 'setting-toggle on' : 'setting-toggle'} onClick={() => void toggle(key)} disabled={saving || (key !== 'enabled' && !settings.enabled)}><span /></button></div>)}</section></> }
