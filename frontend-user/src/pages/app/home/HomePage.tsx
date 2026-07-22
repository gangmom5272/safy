import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { getAreaMap, getAreaStatus, getRecentReports, resolveLocation } from './homeApi'
import { mockLocation, mockMap, mockReports, mockStatus, USE_HOME_MOCK_FALLBACK } from './mockData'
import type { AreaLocation, AreaMapData, AreaStatus, LoadState, RecentReport, RiskLevel } from './types'
import { buildSituationUrl } from './homeUtils'
import './home.css'
import './homeEnhancements.css'

const LATEST_REPORT_SORT = 'latest'
const riskLabels: Record<RiskLevel, string> = { SAFE: '안전', CAUTION: '주의', WARNING: '경고', DANGER: '위험' }
const congestionLabels = { RELAXED: '여유', NORMAL: '보통', CROWDED: '혼잡', VERY_CROWDED: '매우 혼잡' }
const congestionDescriptions = { RELAXED: '평소보다 여유로워요', NORMAL: '평소와 비슷해요', CROWDED: '평소보다 혼잡해요', VERY_CROWDED: '평소보다 82% ↑' }
const formatTime = (value: string | null) => value ? new Date(value).toLocaleString('ko-KR', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }) : '-'
const formatUpdateTime = (value: string | null) => value ? new Date(value).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', hour12: false }) : '-'

export function HomePage() {
  const navigate = useNavigate()
  const mounted = useRef(true)
  const [location, setLocation] = useState<AreaLocation | null>(null)
  const [locationState, setLocationState] = useState<LoadState>('idle')
  const [locationMessage, setLocationMessage] = useState('현재 위치를 확인해 주세요')
  const [map, setMap] = useState<AreaMapData | null>(null)
  const [status, setStatus] = useState<AreaStatus | null>(null)
  const [reports, setReports] = useState<RecentReport[]>([])
  const [states, setStates] = useState({ map: 'idle' as LoadState, status: 'idle' as LoadState, reports: 'idle' as LoadState })
  const [zoom, setZoom] = useState(1)
  const [selectedReport, setSelectedReport] = useState<number | null>(null)

  const loadArea = useCallback(async (area: AreaLocation) => {
    setStates({ map: 'loading', status: 'loading', reports: 'loading' })
    const results = await Promise.allSettled([getAreaMap(area.areaId), getAreaStatus(area.areaId), getRecentReports(area.areaId)])
    if (!mounted.current) return
    const [mapResult, statusResult, reportsResult] = results
    if (mapResult.status === 'fulfilled') { setMap(mapResult.value); setStates((s) => ({ ...s, map: 'success' })) } else { console.error('Home map request failed', mapResult.reason); setMap(USE_HOME_MOCK_FALLBACK ? mockMap : null); setStates((s) => ({ ...s, map: 'error' })) }
    if (statusResult.status === 'fulfilled') { setStatus(statusResult.value); setStates((s) => ({ ...s, status: 'success' })) } else { console.error('Home status request failed', statusResult.reason); setStatus(USE_HOME_MOCK_FALLBACK ? mockStatus : null); setStates((s) => ({ ...s, status: 'error' })) }
    if (reportsResult.status === 'fulfilled') { setReports(reportsResult.value); setStates((s) => ({ ...s, reports: 'success' })) } else { console.error('Home reports request failed', reportsResult.reason); setReports(USE_HOME_MOCK_FALLBACK ? mockReports : []); setStates((s) => ({ ...s, reports: 'error' })) }
  }, [])

  const refreshLocation = useCallback(() => {
    setLocationState('loading'); setLocationMessage('위치 확인 중')
    if (!navigator.geolocation) { setLocationState('error'); setLocationMessage('이 브라우저는 위치 기능을 지원하지 않습니다'); void loadArea(mockLocation); return }
    navigator.geolocation.getCurrentPosition(async ({ coords }) => {
      try { const area = await resolveLocation(coords.latitude, coords.longitude); if (!mounted.current) return; setLocation(area); setLocationState('success'); setLocationMessage('현재 위치 기준'); await loadArea(area) }
      catch (error) { console.error('Home location resolve failed', error); if (!mounted.current) return; setLocation(mockLocation); setLocationState('error'); setLocationMessage('위치 조회 실패 · 기본 지역 표시 중'); await loadArea(mockLocation) }
    }, (error) => { if (!mounted.current) return; console.error('Home geolocation failed', error); setLocationState('error'); setLocationMessage(error.code === error.PERMISSION_DENIED ? '위치 권한 거부 · 기본 지역 표시 중' : '위치 조회 실패 · 기본 지역 표시 중'); setLocation(mockLocation); void loadArea(mockLocation) }, { enableHighAccuracy: true, timeout: 8000 })
  }, [loadArea])

  useEffect(() => {
    mounted.current = true
    const initialLoad = window.setTimeout(refreshLocation, 0)
    return () => { window.clearTimeout(initialLoad); mounted.current = false }
  }, [refreshLocation])
  const retrySection = (section: 'map' | 'status' | 'reports') => {
    if (!location) return
    setStates((s) => ({ ...s, [section]: 'loading' }))
    const request = section === 'map' ? getAreaMap(location.areaId) : section === 'status' ? getAreaStatus(location.areaId) : getRecentReports(location.areaId)
    void request.then((data) => { if (!mounted.current) return; if (section === 'map') setMap(data as AreaMapData); else if (section === 'status') setStatus(data as AreaStatus); else setReports(data as RecentReport[]); setStates((s) => ({ ...s, [section]: 'success' })) }).catch((error: unknown) => { console.error(`Home ${section} retry failed`, error); if (mounted.current) setStates((s) => ({ ...s, [section]: 'error' })) })
  }

  return <div className="home-page">
    <header className="home-location"><div><span>{locationMessage}</span><h1>📍 {location?.areaName ?? '지역 확인 중'}</h1></div><button type="button" onClick={refreshLocation} disabled={locationState === 'loading'}>↻ 새로고침</button></header>
    <section className="home-card"><div className="home-title"><div><span>LIVE AREA</span><h2>현재 지역 상황</h2></div><div className="map-title-actions"><button className="text-button" type="button" disabled={!location} onClick={() => navigate(buildSituationUrl({ location }))}>자세히 보기</button><button type="button" aria-label="지도 확대" onClick={() => setZoom((v) => Math.min(1.4, v + .1))}>＋</button><button type="button" aria-label="지도 축소" onClick={() => setZoom((v) => Math.max(.8, v - .1))}>－</button></div></div>
      {states.map === 'loading' && <div className="home-state">지도를 불러오는 중...</div>}
      {map && states.map !== 'loading' && <><div className="home-map" style={{ transform: `scale(${zoom})` }}>{map.zones.map((zone, index) => <span key={zone.id} className={`zone zone-${zone.congestionLevel.toLowerCase()}`} style={{ left: `${28 + index * 38}%`, top: `${38 + index * 18}%` }}>{zone.name}<small>{congestionLabels[zone.congestionLevel]}</small></span>)}{map.reports.map((report, index) => <button type="button" key={report.id} className={`pin pin-${report.riskLevel.toLowerCase()}`} style={{ left: `${62 - index * 20}%`, top: `${30 + index * 25}%` }} onClick={() => setSelectedReport(report.id)}>!</button>)}</div>{selectedReport && <div className="map-popover">선택한 제보 #{selectedReport}<button type="button" onClick={() => navigate(`/app/reports/${selectedReport}`)}>상세 보기</button></div>}</>}
      {states.map === 'error' && <ErrorNote onRetry={() => retrySection('map')} />}
    </section>
    <section className="home-card status-summary-card"><div className="home-title"><div><span>NOW</span><h2>현재 상황 요약</h2></div><p className="updated">업데이트 {formatUpdateTime(status?.updatedAt ?? null)}</p></div>{states.status === 'loading' ? <div className="home-state">상황을 불러오는 중...</div> : status ? <div className="summary-grid"><StatusMetric icon="◉" value={status.congestionLevel ? congestionLabels[status.congestionLevel] : '정보 없음'} description={status.congestionLevel ? congestionDescriptions[status.congestionLevel] : '혼잡도 정보 없음'} tone={status.congestionLevel?.toLowerCase()} /><StatusMetric icon="!" value={`${status.recentReportCount ?? 0}건`} description={(status.recentReportCount ?? 0) > 0 ? '주의 필요' : '최근 제보 없음'} /><StatusMetric icon="☂" value={status.weather?.temperature != null ? `${status.weather.temperature}℃` : '정보 없음'} description={status.weather?.condition ?? '날씨 정보 없음'} /></div> : <div className="home-state">정보 없음</div>}{states.status === 'error' && <ErrorNote onRetry={() => retrySection('status')} />}</section>
    <section className="home-card"><div className="home-title"><div><span>REPORTS</span><h2>최근 제보</h2></div><button className="text-button" type="button" onClick={() => navigate(buildSituationUrl({ location, sort: LATEST_REPORT_SORT }))}>전체 보기 →</button></div>{states.reports === 'loading' ? <div className="home-state">제보를 불러오는 중...</div> : reports.length ? <div className="recent-list">{reports.slice(0, 3).map((report) => <ReportCard key={report.id} report={report} onOpen={() => navigate(`/app/reports/${report.id}`)} />)}</div> : <div className="home-state">최근 제보가 없습니다</div>}{states.reports === 'error' && <ErrorNote onRetry={() => retrySection('reports')} />}</section>
  </div>
}

function StatusMetric({ icon, value, description, tone }: { icon: string; value: string; description: string; tone?: string }) { return <div className="summary"><span className={`summary-icon${tone ? ` congestion-${tone}` : ''}`} aria-hidden="true">{icon}</span><strong>{value}</strong><small>{description}</small></div> }
function ErrorNote({ onRetry }: { onRetry: () => void }) { return <div className="error-note"><span>최신 정보를 가져오지 못해 예시 정보를 표시합니다.</span><button type="button" onClick={onRetry}>다시 시도</button></div> }

function ReportCard({ report, onOpen }: { report: RecentReport; onOpen: () => void }) {
  const [imageFailed, setImageFailed] = useState(false)
  const thumbnailUrl = report.maskedThumbnailUrl || report.thumbnailUrl

  return <article className="recent-card" tabIndex={0} role="link" onClick={onOpen} onKeyDown={(event) => { if (event.key === 'Enter' || event.key === ' ') onOpen() }}>
    <div className="thumb">
      {thumbnailUrl && !imageFailed ? <img src={thumbnailUrl} alt={`${report.title} 마스킹 현장 이미지`} onError={() => setImageFailed(true)} /> : <span>{imageFailed ? '마스킹 이미지 없음' : '현장 이미지 준비 중'}</span>}
    </div>
    <div><p>{report.reportType} · <em className={`risk-${report.riskLevel.toLowerCase()}`}>{riskLabels[report.riskLevel]}</em></p><h3>{report.title}</h3><small>{report.locationName}<br />{formatTime(report.createdAt)}</small></div>
  </article>
}
