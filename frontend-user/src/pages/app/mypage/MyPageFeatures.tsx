import { useEffect, useState, type Dispatch, type SetStateAction } from 'react'
import { useNavigate } from 'react-router'
import { useFavoritePlacesStore } from '../../../stores/favoritePlacesStore'
import { formatDateTime, formatRelativeTime } from '../../../utils/dateTime'
import { createFavorite, patchFavorite, removeFavorite } from './mypageApi'
import { FavoritePlaceForm } from './FavoritePlaceForm'
import { mockFaqs, mockNotices } from './mypageMock'
import type { FavoriteLocation, Inquiry, MyPageView, MyReport, Notice, ReportStatus } from './mypageTypes'
import { APP_VERSION } from './mypageUtils'
import './favoritePlaceForm.css'

const statusLabel: Record<ReportStatus, string> = { RECEIVED: '검토 중', CHECKING: '확인 중', COMPLETED: '채택됨', REJECTED: '반려됨' }
const reportTypeRules = [
  { pattern: /인파|crowd/i, label: '인파 밀집', titleTerms: ['인파 과밀', '인파 밀집'] },
  { pattern: /교통|traffic/i, label: '교통 혼잡', titleTerms: ['교통 혼잡', '교통 체증'] },
  { pattern: /사고|위험|accident/i, label: '사고·위험', titleTerms: ['사고·위험', '도로 사고', '사고'] },
  { pattern: /화재|연기|fire/i, label: '화재·연기', titleTerms: ['화재·연기', '화재', '연기'] },
  { pattern: /침수|기상|weather/i, label: '침수·기상', titleTerms: ['침수·기상', '침수', '기상'] },
  { pattern: /시설|도로|facility/i, label: '시설물·도로', titleTerms: ['시설물·도로', '시설물 위험', '도로 위험'] },
]
export function PageHeader({ title, onBack, action, className = '' }: { title: string; onBack?: () => void; action?: React.ReactNode; className?: string }) { return <header className={`mypage-header${onBack ? '' : ' main'}${className ? ` ${className}` : ''}`}>{onBack ? <button type="button" aria-label="뒤로가기" onClick={onBack}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m15 5-7 7 7 7"/><path d="M8 12h12"/></svg></button> : <span />}<h1>{title}</h1>{action ?? <span />}</header> }
export function SectionTitle({ title, action, onAction }: { title: string; action?: string; onAction?: () => void }) { return <div className="mypage-title"><h2>{title}</h2>{action && <button type="button" onClick={onAction}>{action} →</button>}</div> }
export function Reports({ reports, onBack, onOpen }: { reports: MyReport[]; onBack: () => void; onOpen: (id: number) => void }) { const [filter, setFilter] = useState<'ALL' | ReportStatus>('ALL'); const filtered = filter === 'ALL' ? reports : reports.filter((item) => item.status === filter); return <><PageHeader title="내 제보 관리" onBack={onBack} /><div className="mypage-tabs" role="tablist">{(['ALL','RECEIVED','CHECKING','COMPLETED'] as const).map((item) => <button type="button" role="tab" aria-selected={filter === item} className={filter === item ? 'active' : ''} key={item} onClick={() => setFilter(item)}>{item === 'ALL' ? '전체' : statusLabel[item]}</button>)}</div>{filtered.length ? <div className="my-report-list">{filtered.map((report) => <button type="button" className="my-report-card" key={report.id} onClick={() => onOpen(report.id)}><span className="report-thumb">{report.maskedThumbnailUrl || report.thumbnailUrl ? <img src={(report.maskedThumbnailUrl || report.thumbnailUrl) ?? ''} alt="제보 현장" /> : '이미지 준비 중'}</span><span><small>{report.type} · {report.locationName}</small><strong>{report.title}</strong><time>{formatRelativeTime(report.createdAt)}</time></span><em className={`status-${report.status.toLowerCase()}`}>{statusLabel[report.status]}</em></button>)}</div> : <Empty text="해당 상태의 제보가 없습니다." />}</> }
export function ReportDetail({ report, onBack }: { report: MyReport; onBack: () => void }) { return <div className="my-report-detail-page"><PageHeader title="내 제보 상세" onBack={onBack} /><section className="mypage-card report-detail-card"><span className="report-detail-media">{report.mediaUrls.length ? <img src={report.mediaUrls[0]} alt="첨부 이미지" /> : '첨부 이미지 없음'}</span><div className="report-detail-heading"><h2>{getReportDisplayTitle(report)}</h2><em className={`status-${report.status.toLowerCase()}`}>{statusLabel[report.status]}</em></div><p className="report-detail-description">{report.description}</p><dl className="report-detail-info"><dt>유형</dt><dd>{getReportTypeLabel(report)}</dd><dt>주소</dt><dd>{report.address}</dd><dt>등록 시간</dt><dd>{formatDateTime(report.createdAt)}</dd></dl></section><section className="mypage-card report-detail-section"><SectionTitle title="관리자 답변" /><p>{report.adminReply ?? '아직 관리자 답변이 등록되지 않았습니다.'}</p></section><section className="mypage-card report-detail-section"><SectionTitle title="처리 이력" /><ol className="report-history-list">{report.history.map((item) => <li key={`${item.status}-${item.changedAt}`}><b>{statusLabel[item.status]}</b><span>{item.description}</span><time>{formatDateTime(item.changedAt)}</time></li>)}</ol></section></div> }
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

  return <><PageHeader title="즐겨찾기" onBack={onBack} action={<button type="button" aria-label="위치 추가" onClick={() => { setEditingItem(null); setAdding(true) }}>＋</button>} />{storageError && <p className="mypage-message" role="status">{storageError}</p>}{adding && <FavoritePlaceForm submitLabel="추가" onSubmit={add} onCancel={() => setAdding(false)} />}{editingItem && <FavoritePlaceForm initialAlias={editingItem.alias} initialLocation={{ id: String(editingItem.areaId), name: editingItem.placeName, address: editingItem.address, latitude: editingItem.latitude, longitude: editingItem.longitude }} submitLabel="저장" onSubmit={edit} onCancel={() => setEditingItem(null)} />}<div className="favorite-list">{favoritePlaces.map((place) => <section className="favorite-card" key={place.id}><button type="button" className="favorite-main" onClick={() => navigate(`/app/situations?query=${encodeURIComponent(place.searchQuery)}`)}><span className="risk-dot risk-safe" /><span><small>{formatDate(place.savedAt)} 저장</small><strong>{place.name}</strong><em>{place.address}</em></span><b>›</b></button><div><button type="button" onClick={() => navigate(`/app/situations?query=${encodeURIComponent(place.searchQuery)}`)}>장소 상황 보기</button><button type="button" className="danger-text" onClick={() => removeFavoritePlace(place.id)}>해제</button></div></section>)}{items.map((item) => <section className="favorite-card" key={item.id}><button type="button" className="favorite-main" onClick={() => onNavigate(item)}><span className={`risk-dot risk-${item.riskLevel.toLowerCase()}`} /><span className="favorite-server-meta"><small>{item.isPrimary ? `대표 위치 · ${item.alias}` : item.alias}</small><strong>{item.placeName}</strong><em>{item.address}</em></span><b>›</b></button><div><button type="button" onClick={() => { setAdding(false); setEditingItem(item) }}>별칭·주소 수정</button><button type="button" onClick={() => { onChange((values) => values.map((value) => ({ ...value, isPrimary: value.id === item.id }))); if (!usingMock) void patchFavorite(item.id, { isPrimary: true }).catch(console.error) }}>대표 설정</button><button type="button" className="danger-text" onClick={() => { if (!window.confirm('즐겨찾기 위치를 삭제할까요?')) return; onChange((values) => values.filter((value) => value.id !== item.id)); if (!usingMock) void removeFavorite(item.id).catch(console.error) }}>삭제</button></div></section>)}</div>{!hasItems && <Empty text="아직 즐겨찾기한 장소가 없습니다." />}</>
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

function getReportTypeLabel(report: MyReport) {
  const rule = reportTypeRules.find((item) => item.pattern.test(`${report.type} ${report.title}`))
  return rule?.label ?? report.type
}

function getReportDisplayTitle(report: MyReport) {
  const locationName = report.locationName.trim()
  if (locationName) return locationName

  const rule = reportTypeRules.find((item) => item.pattern.test(`${report.type} ${report.title}`))
  const titleTerms = rule?.titleTerms ?? [report.type]
  const displayTitle = titleTerms
    .reduce((title, term) => title.replaceAll(term, ''), report.title)
    .replace(/\s{2,}/g, ' ')
    .trim()
  return displayTitle || report.title
}
