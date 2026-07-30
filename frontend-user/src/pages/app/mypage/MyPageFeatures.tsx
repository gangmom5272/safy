import { useEffect, useMemo, useRef, useState, type Dispatch, type SetStateAction } from 'react'
import { useNavigate } from 'react-router'
import { PageHeader } from '../../../components/common/PageHeader'
import { useFavoritePlacesStore } from '../../../stores/favoritePlacesStore'
import { formatDateTime, formatRelativeTime } from '../../../utils/dateTime'
import { createFavorite, patchFavorite, removeFavorite } from './mypageApi'
import { FavoritePlaceForm } from './FavoritePlaceForm'
import { mockFaqs, mockNotices } from './mypageMock'
import type { FavoriteLocation, Inquiry, MyPageView, MyReport, Notice, ReportAttachment, ReportStatus } from './mypageTypes'
import { APP_VERSION, formatReceiptNumber, getReportCategoryLabel, getReportMediaTypeLabel, isVideoAttachment } from './mypageUtils'
import './favoritePlaceForm.css'

// 접수 대기 중(RECEIVED)은 담당자 확인 전이라 시민이 취소할 수 있는 구간이다.
const statusLabel: Record<ReportStatus, string> = { RECEIVED: '접수 대기 중', IN_PROGRESS: '처리중', CLOSED: '처리완료', REJECTED: '반려됨', CANCELLED: '취소됨' }

// 서버에 상태가 추가돼 모르는 값이 와도 배지가 빈칸으로 남지 않게 기본 문구를 준다.
function getStatusLabel(status: ReportStatus) {
  return statusLabel[status] ?? '상태 확인 중'
}
type ReportDisplayStatus = 'ALL' | ReportStatus
type ReportDateFilter = 'ALL' | '7_DAYS' | '1_MONTH' | '3_MONTHS' | 'CUSTOM'

// 취소됨은 사용자가 고를 필요가 없는 상태라 탭에서만 뺐다. CANCELLED 상태값과 취소 API 는
// 상세 화면의 제보 취소가 계속 쓰므로 statusLabel·ReportStatus 에 그대로 남겨 둔다.
// 전체 탭은 서버가 주는 목록을 그대로 보여주므로 취소된 제보도 여기에 포함된다.
const reportStatusFilters: { value: ReportDisplayStatus; label: string }[] = [
  { value: 'ALL', label: '전체' },
  { value: 'RECEIVED', label: '접수 대기 중' },
  { value: 'IN_PROGRESS', label: '처리중' },
  { value: 'CLOSED', label: '처리완료' },
  { value: 'REJECTED', label: '반려됨' },
]

// 상태값을 CSS 클래스로 쓸 때는 다른 클래스와 같은 kebab-case 로 맞춘다(IN_PROGRESS → in-progress).
function toStatusModifier(status: ReportStatus) {
  return status.toLowerCase().replace('_', '-')
}
const reportDateFilters: { value: ReportDateFilter; label: string }[] = [
  { value: 'ALL', label: '전체 기간' },
  { value: '7_DAYS', label: '최근 7일' },
  { value: '1_MONTH', label: '최근 1개월' },
  { value: '3_MONTHS', label: '최근 3개월' },
  { value: 'CUSTOM', label: '직접 설정' },
]

export function SectionTitle({ title, action, onAction }: { title: string; action?: string; onAction?: () => void }) { return <div className="mypage-title"><h2>{title}</h2>{action && <button type="button" onClick={onAction}>{action} →</button>}</div> }
export function Reports({ reports, onBack, onOpen }: { reports: MyReport[]; onBack: () => void; onOpen: (id: number) => void }) {
  const [statusFilter, setStatusFilter] = useState<ReportDisplayStatus>('ALL')
  const [dateFilter, setDateFilter] = useState<ReportDateFilter>('ALL')
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [dateMenuOpen, setDateMenuOpen] = useState(false)
  const dateMenuRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!dateMenuOpen) return
    const closeOnOutsideClick = (event: MouseEvent) => {
      if (!dateMenuRef.current?.contains(event.target as Node)) setDateMenuOpen(false)
    }
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setDateMenuOpen(false)
    }
    document.addEventListener('mousedown', closeOnOutsideClick)
    document.addEventListener('keydown', closeOnEscape)
    return () => {
      document.removeEventListener('mousedown', closeOnOutsideClick)
      document.removeEventListener('keydown', closeOnEscape)
    }
  }, [dateMenuOpen])

  const filteredReports = useMemo(() => reports.filter((report) => {
    if (statusFilter !== 'ALL' && report.status !== statusFilter) return false
    if (dateFilter === 'ALL') return true

    const createdAt = new Date(report.createdAt)
    const now = new Date()
    if (dateFilter === 'CUSTOM') {
      const start = startDate ? new Date(`${startDate}T00:00:00`) : null
      const end = endDate ? new Date(`${endDate}T23:59:59.999`) : null
      return (!start || createdAt >= start) && (!end || createdAt <= end)
    }

    const cutoff = new Date(now)
    if (dateFilter === '7_DAYS') cutoff.setDate(now.getDate() - 7)
    if (dateFilter === '1_MONTH') cutoff.setMonth(now.getMonth() - 1)
    if (dateFilter === '3_MONTHS') cutoff.setMonth(now.getMonth() - 3)
    return createdAt >= cutoff && createdAt <= now
  }), [dateFilter, endDate, reports, startDate, statusFilter])

  const selectedDateLabel = reportDateFilters.find((item) => item.value === dateFilter)?.label ?? '전체 기간'

  return <><PageHeader title="내 제보 관리" onBack={onBack} /><div className="report-filters"><div className="report-status-filters" role="tablist" aria-label="제보 상태">{reportStatusFilters.map((item) => <button type="button" role="tab" aria-selected={statusFilter === item.value} className={statusFilter === item.value ? 'active' : ''} key={item.value} onClick={() => setStatusFilter(item.value)}>{item.label}</button>)}</div><div className="report-filter-toolbar"><span>조회 기간</span><div className="report-date-filter" ref={dateMenuRef}><button type="button" className="report-date-filter__trigger" aria-haspopup="listbox" aria-expanded={dateMenuOpen} onClick={() => setDateMenuOpen((open) => !open)}><span>{selectedDateLabel}</span><span aria-hidden="true">⌄</span></button>{dateMenuOpen && <div className="report-date-filter__menu" role="listbox" aria-label="제보 기간">{reportDateFilters.map((item) => <button type="button" role="option" aria-selected={dateFilter === item.value} className={dateFilter === item.value ? 'active' : ''} key={item.value} onClick={() => { setDateFilter(item.value); setDateMenuOpen(false) }}>{item.label}</button>)}</div>}</div></div>{dateFilter === 'CUSTOM' && <div className="report-custom-dates"><label>시작일<input type="date" value={startDate} max={endDate || undefined} onChange={(event) => setStartDate(event.target.value)} /></label><span aria-hidden="true">~</span><label>종료일<input type="date" value={endDate} min={startDate || undefined} onChange={(event) => setEndDate(event.target.value)} /></label></div>}</div>{filteredReports.length ? <div className="my-report-list">{filteredReports.map((report) => <button type="button" className="my-report-card" key={report.id} onClick={() => onOpen(report.id)}><span className="report-thumb"><em className={`report-card-status report-card-status--${toStatusModifier(report.status)}`}>{getStatusLabel(report.status)}</em><time className="report-card-relative-date" dateTime={report.createdAt}>{formatRelativeTime(report.createdAt)}</time>{report.maskedThumbnailUrl || report.thumbnailUrl ? <img src={(report.maskedThumbnailUrl || report.thumbnailUrl) ?? ''} alt="제보 현장" /> : <span className="report-image-placeholder">이미지 준비중</span>}</span><span className="report-card-body"><small>{report.type} · {report.locationName}</small><strong>{report.title}</strong><time dateTime={report.createdAt}>{formatDateTime(report.createdAt)}</time></span></button>)}</div> : <Empty text="선택한 조건에 해당하는 제보가 없습니다." />}</>
}
// 첨부는 여러 개일 수 있다. 가로 스크롤 + scroll-snap 으로 스와이프를 만들고, 현재
// 위치는 스크롤 위치에서 계산한다. 캐러셀 라이브러리를 새로 넣지 않으려는 선택이다.
function ReportMediaCarousel({ attachments }: { attachments: ReportAttachment[] }) {
  const trackRef = useRef<HTMLDivElement | null>(null)
  const [activeIndex, setActiveIndex] = useState(0)
  const [failedUrls, setFailedUrls] = useState<string[]>([])

  if (!attachments.length) {
    return <p className="report-detail-media report-detail-media--empty">첨부 파일 없음</p>
  }

  const moveTo = (index: number) => {
    const track = trackRef.current
    if (!track) return

    const nextIndex = Math.min(Math.max(index, 0), attachments.length - 1)
    track.scrollTo({ left: track.clientWidth * nextIndex, behavior: 'smooth' })
    setActiveIndex(nextIndex)
  }

  const handleScroll = () => {
    const track = trackRef.current
    if (!track || !track.clientWidth) return

    setActiveIndex(Math.round(track.scrollLeft / track.clientWidth))
  }

  const hasMany = attachments.length > 1

  return (
    <div className="report-detail-media-carousel">
      <div
        className="report-detail-media-track"
        ref={trackRef}
        onScroll={handleScroll}
        role={hasMany ? 'group' : undefined}
        aria-label={hasMany ? `첨부 파일 ${attachments.length}개` : undefined}
      >
        {attachments.map((attachment, index) => (
          <div className="report-detail-media" key={attachment.url}>
            {failedUrls.includes(attachment.url) ? (
              <p className="report-detail-media-failed">첨부 파일을 불러오지 못했습니다.</p>
            ) : isVideoAttachment(attachment) ? (
              <video
                src={attachment.url}
                controls
                playsInline
                preload="metadata"
                onError={() => setFailedUrls((urls) => [...urls, attachment.url])}
              />
            ) : (
              <img
                src={attachment.url}
                alt={attachment.filename ?? `첨부 파일 ${index + 1}`}
                onError={() => setFailedUrls((urls) => [...urls, attachment.url])}
              />
            )}
          </div>
        ))}
      </div>

      {hasMany ? (
        <>
          <span className="report-detail-media-counter" aria-live="polite">
            {activeIndex + 1} / {attachments.length}
          </span>
          <button
            type="button"
            className="report-detail-media-nav prev"
            aria-label="이전 첨부 파일"
            disabled={activeIndex === 0}
            onClick={() => moveTo(activeIndex - 1)}
          >
            ‹
          </button>
          <button
            type="button"
            className="report-detail-media-nav next"
            aria-label="다음 첨부 파일"
            disabled={activeIndex === attachments.length - 1}
            onClick={() => moveTo(activeIndex + 1)}
          >
            ›
          </button>
          <div className="report-detail-media-dots">
            {attachments.map((attachment, index) => (
              <button
                type="button"
                key={attachment.url}
                className={index === activeIndex ? 'active' : ''}
                aria-label={`${index + 1}번째 첨부 파일 보기`}
                aria-current={index === activeIndex}
                onClick={() => moveTo(index)}
              />
            ))}
          </div>
        </>
      ) : null}
    </div>
  )
}

// 제보 내용 제목에 붙는 작성 아이콘. 프로필 수정 버튼과 같은 연필 모양을 쓴다.
function WriteIcon() { return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m4 20 4.5-1 10-10a2.1 2.1 0 0 0-3-3l-10 10Z"/><path d="m14 7 3 3"/></svg> }

function getCancelButtonLabel(cancelled: boolean, cancelling: boolean) {
  if (cancelled) return '제보가 정상적으로 취소되었습니다.'
  if (cancelling) return '취소 처리 중...'

  return '제보 취소'
}

export function ReportDetail({ report, onBack, onCancel }: { report: MyReport; onBack: () => void; onCancel?: () => Promise<void> }) {
  // 취소 요청이 끝날 때까지 버튼을 잠가 같은 제보를 두 번 취소하지 않게 한다.
  const [cancelling, setCancelling] = useState(false)
  const content = report.description?.trim()
  // 취소 완료는 서버가 준 상태(CANCELLED)로만 판단한다. 로컬 플래그를 쓰지 않으므로
  // 새로고침해도, 이미 취소된 제보로 바로 들어와도 같은 문구가 그대로 보인다.
  const cancelled = report.status === 'CANCELLED'
  const cancellable = report.status === 'RECEIVED' && Boolean(onCancel)

  return <div className="my-report-detail-page">
    <PageHeader title="내 제보 상세" onBack={onBack} />
    <ReportMediaCarousel attachments={report.attachments} />
    <section className="report-detail-section">
      <div className="report-detail-heading">
        <h2 className="report-detail-section__title">{formatReceiptNumber(report.id)}</h2>
        <em className={`status-${toStatusModifier(report.status)}`}>{getStatusLabel(report.status)}</em>
      </div>
      <div className="mypage-card report-detail-card">
        <dl className="report-detail-info">
          <dt>주소</dt>
          <dd>{report.address}</dd>
          <dt>등록 시간</dt>
          <dd>{formatDateTime(report.createdAt)}</dd>
          <dt>유형 · 카테고리</dt>
          <dd>{getReportMediaTypeLabel(report)} · {getReportCategoryLabel(report.category)}</dd>
        </dl>
        <div className="report-detail-content">
          <h3><WriteIcon />제보 내용</h3>
          <p className="report-detail-description">{content || '작성된 제보 내용이 없습니다.'}</p>
        </div>
        {cancelled || cancellable ? <button type="button" className={cancelled ? 'report-detail-cancel is-cancelled' : 'report-detail-cancel'} disabled={cancelled || cancelling} onClick={() => { if (!onCancel) return; setCancelling(true); void onCancel().finally(() => setCancelling(false)) }}>{getCancelButtonLabel(cancelled, cancelling)}</button> : null}
      </div>
    </section>
    <section className="report-detail-section"><h2 className="report-detail-section__title">관리자 답변</h2><div className="mypage-card report-detail-section__card"><p>{report.adminReply ?? '아직 관리자 답변이 등록되지 않았습니다.'}</p></div></section>
    <section className="report-detail-section"><h2 className="report-detail-section__title">처리 이력</h2><div className="mypage-card report-detail-section__card">{report.history.length ? <ol className="report-history-list">{report.history.map((item) => <li key={`${item.status}-${item.changedAt}`}><b>{getStatusLabel(item.status)}</b><span>{item.description}</span><time>{formatDateTime(item.changedAt)}</time></li>)}</ol> : <p>등록된 처리 이력이 없습니다.</p>}</div></section>
  </div>
}

// 즐겨찾기 추가 버튼 아이콘. 원형 버튼 가운데에 정확히 놓이도록 문자 대신 svg 를 쓴다.
function PlusIcon() { return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M12 5v14M5 12h14"/></svg> }

export function Favorites({ items, onBack, onChange, onNavigate, usingMock }: { items: FavoriteLocation[]; onBack: () => void; onChange: Dispatch<SetStateAction<FavoriteLocation[]>>; onNavigate: (item: FavoriteLocation) => void; usingMock: boolean }) {
  const navigate = useNavigate()
  const { favoritePlaces, storageError, hydrate, removeFavoritePlace } = useFavoritePlacesStore()
  const [adding, setAdding] = useState(false)
  const [editingItem, setEditingItem] = useState<FavoriteLocation | null>(null)

  useEffect(() => {
    hydrate()
  }, [hydrate])

  const add = (alias: string, location: { name: string; address: string; latitude?: number; longitude?: number }) => {
    if (typeof location.latitude !== 'number' || typeof location.longitude !== 'number') return
    const data = { placeName: location.name, alias, address: location.address, latitude: location.latitude, longitude: location.longitude }
    const next: FavoriteLocation = { id: Date.now(), areaId: Date.now(), ...data, riskLevel: 'SAFE', isPrimary: items.length === 0 }
    onChange((values) => [...values, next])
    setAdding(false)
    if (!usingMock) void createFavorite(data).catch(console.error)
  }

  const edit = (alias: string, location: { name: string; address: string; latitude?: number; longitude?: number }) => {
    if (!editingItem || typeof location.latitude !== 'number' || typeof location.longitude !== 'number') return
    const changes = { alias, placeName: location.name, address: location.address, latitude: location.latitude, longitude: location.longitude }
    onChange((values) => values.map((value) => value.id === editingItem.id ? { ...value, ...changes } : value))
    if (!usingMock) void patchFavorite(editingItem.id, changes).catch(console.error)
    setEditingItem(null)
  }

  const hasItems = favoritePlaces.length > 0 || items.length > 0

  const openStoredFavorite = (searchQuery: string) => navigate(`/app/situations?query=${encodeURIComponent(searchQuery)}`, { state: { from: 'favorites' } })

  return <><PageHeader title="즐겨찾기" className="favorites-header" onBack={onBack} action={<button type="button" className="favorite-add-button" aria-label="즐겨찾기 추가" onClick={() => { setEditingItem(null); setAdding(true) }}><PlusIcon /></button>} />{storageError && <p className="mypage-message" role="status">{storageError}</p>}{adding && <FavoritePlaceForm submitLabel="추가" onSubmit={add} onCancel={() => setAdding(false)} />}{editingItem && <FavoritePlaceForm initialAlias={editingItem.alias} initialLocation={{ id: String(editingItem.areaId), name: editingItem.placeName, address: editingItem.address, latitude: editingItem.latitude, longitude: editingItem.longitude }} submitLabel="저장" onSubmit={edit} onCancel={() => setEditingItem(null)} />}<div className="favorite-list">{favoritePlaces.map((place) => <section className="favorite-card" key={place.id}><button type="button" className="favorite-main" onClick={() => openStoredFavorite(place.searchQuery)}><span className="risk-dot risk-safe" /><span><small>{formatDate(place.savedAt)} 저장</small><strong>{place.name}</strong><em>{place.address}</em></span><b>›</b></button><div><button type="button" onClick={() => openStoredFavorite(place.searchQuery)}>장소 상황 보기</button><button type="button" className="danger-text" onClick={() => removeFavoritePlace(place.id)}>해제</button></div></section>)}{items.map((item) => <section className="favorite-card" key={item.id}><button type="button" className="favorite-main" onClick={() => onNavigate(item)}><span className={`risk-dot risk-${item.riskLevel.toLowerCase()}`} /><span className="favorite-server-meta"><small>{item.isPrimary ? `대표 위치 · ${item.alias}` : item.alias}</small><strong>{item.placeName}</strong><em>{item.address}</em></span><b>›</b></button><div><button type="button" onClick={() => { setAdding(false); setEditingItem(item) }}>별칭·주소 수정</button><button type="button" onClick={() => { onChange((values) => values.map((value) => ({ ...value, isPrimary: value.id === item.id }))); if (!usingMock) void patchFavorite(item.id, { isPrimary: true }).catch(console.error) }}>대표 설정</button><button type="button" className="danger-text" onClick={() => { if (!window.confirm('즐겨찾기 위치를 삭제할까요?')) return; onChange((values) => values.filter((value) => value.id !== item.id)); if (!usingMock) void removeFavorite(item.id).catch(console.error) }}>삭제</button></div></section>)}</div>{!hasItems && <Empty text="아직 즐겨찾기한 장소가 없습니다." />}</>
}
export function Support({ onBack, onGo }: { onBack: () => void; onGo: (view: MyPageView) => void }) { const items: [SupportIconName, string, MyPageView][] = [['help','자주 묻는 질문','FAQ'],['notice','공지사항','NOTICES'],['inquiry','1:1 문의','INQUIRY_FORM'],['history','문의 내역','INQUIRIES']]; return <><PageHeader title="고객센터" onBack={onBack} className="support-header" /><section className="mypage-menu support-menu">{items.map(([icon,label,target]) => <button type="button" key={target} onClick={() => onGo(target)}><span><SupportIcon name={icon} /></span><b>{label}</b><em><ChevronRightIcon /></em></button>)}</section></> }

type SupportIconName = 'help' | 'notice' | 'inquiry' | 'history'

function SupportIcon({ name }: { name: SupportIconName }) {
  const paths = {
    help: <><circle cx="12" cy="12" r="9"/><path d="M9.7 9a2.5 2.5 0 1 1 3.7 2.2c-.9.5-1.4 1.1-1.4 2.3M12 17h.01"/></>,
    notice: <><path d="m3 11 15-5v12L3 13Z"/><path d="M7 14v5h4v-6"/></>,
    inquiry: <><path d="M21 15a4 4 0 0 1-4 4H8l-5 3V7a4 4 0 0 1 4-4h10a4 4 0 0 1 4 4Z"/><path d="M8 9h8M8 13h5"/></>,
    history: <><path d="M9 4h6M9 2v4M15 2v4"/><rect x="4" y="4" width="16" height="17" rx="2"/><path d="M8 10h8M8 14h5"/></>,
  }
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name]}</svg>
}

function ChevronRightIcon() { return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m9 5 7 7-7 7"/></svg> }
export function Faqs({ onBack }: { onBack: () => void }) { const categories = ['전체', ...new Set(mockFaqs.map((item) => item.category))]; const [category, setCategory] = useState('전체'); const [open, setOpen] = useState<number | null>(null); const items = category === '전체' ? mockFaqs : mockFaqs.filter((item) => item.category === category); return <><PageHeader title="FAQ" onBack={onBack} /><div className="mypage-tabs" role="tablist">{categories.map((item) => <button type="button" role="tab" aria-selected={item === category} className={item === category ? 'active' : ''} key={item} onClick={() => setCategory(item)}>{item}</button>)}</div><div className="accordion-list">{items.map((item) => <section key={item.id}><button type="button" aria-expanded={open === item.id} onClick={() => setOpen(open === item.id ? null : item.id)}><span>Q</span><b>{item.question}</b><em>{open === item.id ? '−' : '+'}</em></button>{open === item.id && <p>{item.answer}</p>}</section>)}</div></> }
export function Notices({ onBack, onOpen }: { onBack: () => void; onOpen: (notice: Notice) => void }) { return <><PageHeader title="공지사항" onBack={onBack} /><div className="notice-list">{mockNotices.map((item) => <button type="button" key={item.id} onClick={() => onOpen(item)}>{item.isImportant && <em>중요</em>}<strong>{item.title}</strong><time>{formatRelativeTime(item.createdAt)}</time><b>›</b></button>)}</div></> }
export function NoticeDetail({ notice, onBack }: { notice: Notice; onBack: () => void }) { return <SimplePage title="공지 상세" onBack={onBack}><article className="text-detail"><small>{formatDateTime(notice.createdAt)}</small><h2>{notice.title}</h2><p>{notice.content}</p></article></SimplePage> }
export function InquiryForm({ onBack, onSubmit }: { onBack: () => void; onSubmit: (item: Inquiry) => void }) { const [type, setType] = useState('서비스'); const [title, setTitle] = useState(''); const [content, setContent] = useState(''); const [file, setFile] = useState(''); return <><PageHeader title="1:1 문의" onBack={onBack} /><form className="mypage-form" onSubmit={(event) => { event.preventDefault(); onSubmit({ id: Date.now(), type, title, content, attachmentName: file, createdAt: new Date().toISOString(), status: 'WAITING' }) }}><label>문의 유형<select value={type} onChange={(event) => setType(event.target.value)}><option>서비스</option><option>제보</option><option>계정</option><option>기타</option></select></label><label>제목<input required value={title} onChange={(event) => setTitle(event.target.value)} /></label><label>내용<textarea required rows={6} value={content} onChange={(event) => setContent(event.target.value)} /></label><label>첨부파일<input type="file" onChange={(event) => setFile(event.target.files?.[0]?.name ?? '')} /><small>{file || '선택된 파일 없음'}</small></label><button type="submit">문의 등록</button></form></> }
export function Inquiries({ items, onBack, onOpen }: { items: Inquiry[]; onBack: () => void; onOpen: (item: Inquiry) => void }) { return <><PageHeader title="문의 내역" onBack={onBack} /><div className="notice-list">{items.map((item) => <button type="button" key={item.id} onClick={() => onOpen(item)}><em className={item.status === 'ANSWERED' ? 'answered' : ''}>{item.status === 'ANSWERED' ? '답변 완료' : '답변 대기'}</em><strong>{item.title}</strong><time>{formatDate(item.createdAt)}</time><b>›</b></button>)}</div>{!items.length && <Empty text="문의 내역이 없습니다." />}</> }
export function InquiryDetail({ item, onBack }: { item: Inquiry; onBack: () => void }) { return <SimplePage title="문의 상세" onBack={onBack}><article className="text-detail"><small>{item.type} · {formatDateTime(item.createdAt)}</small><h2>{item.title}</h2><p>{item.content}</p>{item.attachmentName && <span>첨부: {item.attachmentName}</span>}</article><section className="mypage-card"><SectionTitle title="답변" /><p>{item.answer ?? '답변을 준비하고 있습니다.'}</p>{item.answeredAt && <small>{formatDateTime(item.answeredAt)}</small>}</section></SimplePage> }
export function Guide({ onBack }: { onBack: () => void }) { const sections = [{ title: '서비스 이용 방법', content: '현재 지역을 확인하고 지도와 실시간 안전 정보를 살펴보세요.' }, { title: '서비스 소개', content: 'SAFE:ON은 시민과 함께 만드는 지역 안전 정보 서비스입니다.' }, { title: '제보 방법', content: '하단 제보하기 메뉴에서 유형과 위치, 현장 정보를 등록할 수 있어요.' }, { title: '안전 수칙', content: '혼잡 지역에서는 안내에 따르고 위험 현장과 충분한 거리를 유지하세요.' }, { title: '개인정보처리방침', content: '서비스 제공에 필요한 최소한의 개인정보만 안전하게 처리합니다.' }, { title: '이용약관', content: '정확하고 건전한 제보로 모두의 안전에 함께해 주세요.' }]; const [open, setOpen] = useState<string | null>(null); return <><PageHeader title="이용 안내" onBack={onBack} /><div className="accordion-list">{sections.map((item) => <section key={item.title}><button type="button" aria-expanded={open === item.title} onClick={() => setOpen(open === item.title ? null : item.title)}><span>i</span><b>{item.title}</b><em>{open === item.title ? '−' : '+'}</em></button>{open === item.title && <p>{item.content}</p>}</section>)}</div><p className="version">SAFE:ON 버전 {APP_VERSION}</p></> }
function SimplePage({ title, onBack, children }: { title: string; onBack: () => void; children: React.ReactNode }) { return <><PageHeader title={title} onBack={onBack} />{children}</> }
function Empty({ text }: { text: string }) { return <div className="mypage-empty"><span>○</span><p>{text}</p></div> }
function formatDate(value: string) { return formatRelativeTime(value) }
