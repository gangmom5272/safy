import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import type { AdminReport, RiskLevel } from '../../types/admin'
import {
  getRiskConfig,
  matchesRiskLevel,
  RISK_LEVEL_CONFIG,
  RISK_LEVEL_OPTIONS,
  RISK_LEVEL_ORDER,
} from '../../utils/adminRisk'
import { ReportMap } from '../../components/admin/ReportMap'
import { hasCoordinates } from '../../utils/reportGeo'
import { createReportDetailPath } from '../../router/paths'
import { formatAdminDate } from '../../utils/adminDate'
import { getDashboardReports } from '../reports/reportApi'
import type { KakaoCustomOverlay, KakaoMap, KakaoMaps, KakaoPolygon } from '../../services/kakaoMap'
import {
  getGridRisks,
  getGridRiskTrend,
  resolutionForLevel,
  snapBounds,
  type GridRisk,
  type GridRiskTrendPoint,
} from './gridRiskApi'
import {
  congestionConfig,
  getOngoingEvents,
  getPlaceCongestions,
  PLACE_CATEGORY_LABELS,
  type CulturalEvent,
  type PlaceCongestion,
} from './publicDataApi'
import { GridTrendChart } from './GridTrendChart'
import { isNewReport } from '../../utils/adminVerification'

const allValue = 'all'
const unsetValue = ''
// 지도 이동·줌마다 바로 조회하면 드래그 중 요청이 쏟아진다. 계약 권고값(200~300ms).
const VIEWPORT_DEBOUNCE_MS = 250

interface MapFilters {
  region: string
  riskLevel: RiskLevel | typeof allValue | typeof unsetValue
}

const initialMapFilters: MapFilters = {
  region: '',
  riskLevel: unsetValue,
}

/** 같은 등급 안에서 score 로 농도를 나눈다. 등급만 쓰면 45점과 60점이 같은 색이 된다. */
function fillOpacityForScore(score: number) {
  const ratio = Math.min(Math.max(score, 0), 100) / 100
  return 0.18 + ratio * 0.42
}

function LocationIcon() {
  return (
    <svg className="monitoring-filter__location-icon" viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false">
      <path d="M20 10c0 5-8 11-8 11S4 15 4 10a8 8 0 1 1 16 0Z" />
      <circle cx="12" cy="10" r="2.5" />
    </svg>
  )
}

export function RiskMapPage() {
  const navigate = useNavigate()
  const [draftFilters, setDraftFilters] = useState<MapFilters>(initialMapFilters)
  const [appliedFilters, setAppliedFilters] = useState<MapFilters>(initialMapFilters)
  const [showGridLayer, setShowGridLayer] = useState(true)
  const [showReportLayer, setShowReportLayer] = useState(true)
  const [showCongestionLayer, setShowCongestionLayer] = useState(true)
  const [showEventLayer, setShowEventLayer] = useState(false)
  const [reports, setReports] = useState<AdminReport[]>([])
  const [isLoading, setIsLoading] = useState(true)

  const [gridRisks, setGridRisks] = useState<GridRisk[]>([])
  const [gridError, setGridError] = useState('')
  const [selectedCell, setSelectedCell] = useState<GridRisk | null>(null)
  const [trend, setTrend] = useState<GridRiskTrendPoint[] | null>(null)
  const [trendError, setTrendError] = useState('')

  const [places, setPlaces] = useState<PlaceCongestion[]>([])
  const [events, setEvents] = useState<CulturalEvent[]>([])
  const [selectedPlace, setSelectedPlace] = useState<PlaceCongestion | null>(null)
  const [selectedEvent, setSelectedEvent] = useState<CulturalEvent | null>(null)

  const [isMapReady, setIsMapReady] = useState(false)
  const mapApiRef = useRef<{ map: KakaoMap; maps: KakaoMaps } | null>(null)
  const polygonsRef = useRef<KakaoPolygon[]>([])
  const congestionOverlaysRef = useRef<KakaoCustomOverlay[]>([])
  const eventOverlaysRef = useRef<KakaoCustomOverlay[]>([])
  const debounceTimerRef = useRef<number | null>(null)
  const abortRef = useRef<AbortController | null>(null)

  useEffect(() => {
    let active = true

    getDashboardReports(0, 100)
      .then((loaded) => { if (active) setReports(loaded) })
      .catch(() => undefined)
      .finally(() => { if (active) setIsLoading(false) })

    // 121곳 혼잡도와 행사 목록은 뷰포트와 무관하게 한 번만 받는다.
    getPlaceCongestions()
      .then((loaded) => { if (active) setPlaces(loaded) })
      .catch(() => undefined)
    getOngoingEvents()
      .then((loaded) => { if (active) setEvents(loaded) })
      .catch(() => undefined)

    return () => { active = false }
  }, [])

  const loadGridRisks = useCallback(async () => {
    const mapApi = mapApiRef.current
    if (!mapApi) return

    const bounds = mapApi.map.getBounds()
    const southWest = bounds.getSouthWest()
    const northEast = bounds.getNorthEast()
    const resolution = resolutionForLevel(mapApi.map.getLevel())
    // bbox 를 격자 경계로 스냅해야 같은 칸 범위를 보는 동안 URL 이 같아져 3분 캐시가 듣는다.
    const snapped = snapBounds(
      {
        minLat: southWest.getLat(),
        maxLat: northEast.getLat(),
        minLng: southWest.getLng(),
        maxLng: northEast.getLng(),
      },
      resolution,
    )

    abortRef.current?.abort()
    const controller = new AbortController()
    abortRef.current = controller

    try {
      const risks = await getGridRisks(snapped, resolution, controller.signal)
      if (controller.signal.aborted) return
      setGridRisks(risks)
      setGridError('')
    } catch (error) {
      if (controller.signal.aborted) return
      setGridError(error instanceof Error ? error.message : '격자 위험도를 불러오지 못했습니다.')
    }
  }, [])

  const scheduleGridReload = useCallback(() => {
    if (debounceTimerRef.current !== null) window.clearTimeout(debounceTimerRef.current)
    debounceTimerRef.current = window.setTimeout(() => {
      debounceTimerRef.current = null
      void loadGridRisks()
    }, VIEWPORT_DEBOUNCE_MS)
  }, [loadGridRisks])

  const handleMapReady = useCallback((map: KakaoMap, maps: KakaoMaps) => {
    mapApiRef.current = { map, maps }
    setIsMapReady(true)
    // idle 은 이동·줌이 끝날 때마다 온다. 첫 화면은 idle 이 오기 전에도 그려야 하므로
    // 준비 직후 한 번 직접 조회한다.
    maps.event.addListener(map, 'idle', scheduleGridReload)
    void loadGridRisks()
  }, [loadGridRisks, scheduleGridReload])

  useEffect(() => () => {
    if (debounceTimerRef.current !== null) window.clearTimeout(debounceTimerRef.current)
    abortRef.current?.abort()
    polygonsRef.current.forEach((polygon) => polygon.setMap(null))
    polygonsRef.current = []
    congestionOverlaysRef.current.forEach((overlay) => overlay.setMap(null))
    congestionOverlaysRef.current = []
    eventOverlaysRef.current.forEach((overlay) => overlay.setMap(null))
    eventOverlaysRef.current = []
    mapApiRef.current = null
  }, [])

  const openTrend = useCallback((cell: GridRisk) => {
    setSelectedCell(cell)
    setSelectedPlace(null)
    setSelectedEvent(null)
    setTrend(null)
    setTrendError('')
    getGridRiskTrend(cell.gridCode)
      .then(setTrend)
      .catch((error: unknown) => {
        setTrendError(error instanceof Error ? error.message : '추이를 불러오지 못했습니다.')
      })
  }, [])

  const clearSelection = useCallback(() => {
    setSelectedCell(null)
    setSelectedPlace(null)
    setSelectedEvent(null)
    setTrend(null)
    setTrendError('')
  }, [])

  // 격자 폴리곤 렌더링. 응답에 있는 칸만 그린다 — 없는 칸은 근거가 없는 칸이지
  // 안전한 칸이 아니므로 0점으로 채워 색칠하면 안 된다.
  useEffect(() => {
    const mapApi = mapApiRef.current
    if (!mapApi) return

    polygonsRef.current.forEach((polygon) => polygon.setMap(null))
    polygonsRef.current = []

    if (!showGridLayer) return

    const { map, maps } = mapApi
    gridRisks.forEach((risk) => {
      const config = getRiskConfig(risk.congestionLevel, risk.score)
      if (!config) return

      // 경계 좌표를 그대로 쓴다. gridCode 를 파싱해 역산하면 산식이 어긋났을 때
      // 조용히 다른 칸을 그리게 된다.
      const polygon = new maps.Polygon({
        map,
        path: [
          new maps.LatLng(risk.southWestLatitude, risk.southWestLongitude),
          new maps.LatLng(risk.southWestLatitude, risk.northEastLongitude),
          new maps.LatLng(risk.northEastLatitude, risk.northEastLongitude),
          new maps.LatLng(risk.northEastLatitude, risk.southWestLongitude),
        ],
        strokeWeight: 1,
        strokeColor: config.markerColor,
        strokeOpacity: 0.7,
        fillColor: config.markerColor,
        fillOpacity: fillOpacityForScore(risk.score),
        zIndex: 1,
      })
      maps.event.addListener(polygon, 'click', () => openTrend(risk))
      polygonsRef.current.push(polygon)
    })
  }, [gridRisks, isMapReady, openTrend, showGridLayer])

  // 혼잡도 레이어: 서울시 121장소를 장소명 라벨 마커로 보여준다. 좌표 없는 항목은 건너뛴다.
  useEffect(() => {
    const mapApi = mapApiRef.current
    if (!isMapReady || !mapApi) return

    congestionOverlaysRef.current.forEach((overlay) => overlay.setMap(null))
    congestionOverlaysRef.current = []

    if (!showCongestionLayer) return

    const { map, maps } = mapApi
    places.forEach((place) => {
      if (place.latitude === null || place.longitude === null) return
      const config = congestionConfig(place.congestionLevel)
      if (!config) return

      const marker = document.createElement('button')
      marker.type = 'button'
      marker.className = 'congestion-place-marker'
      marker.style.setProperty('--congestion-color', config.color)
      marker.innerHTML = `<i aria-hidden="true"></i><span></span>`
      marker.querySelector('span')!.textContent = place.placeName
      marker.setAttribute('aria-label', `${place.placeName} 혼잡도 ${config.label} 상세 보기`)
      marker.addEventListener('click', () => {
        setSelectedPlace(place)
        setSelectedCell(null)
        setSelectedEvent(null)
      })

      const overlay = new maps.CustomOverlay({
        position: new maps.LatLng(place.latitude, place.longitude),
        content: marker,
        map,
        yAnchor: 1,
        zIndex: 4,
        clickable: true,
      })
      congestionOverlaysRef.current.push(overlay)
    })
  }, [isMapReady, places, showCongestionLayer])

  // 행사 레이어: 진행 중인 문화 행사를 라벨 마커로 보여준다.
  useEffect(() => {
    const mapApi = mapApiRef.current
    if (!isMapReady || !mapApi) return

    eventOverlaysRef.current.forEach((overlay) => overlay.setMap(null))
    eventOverlaysRef.current = []

    if (!showEventLayer) return

    const { map, maps } = mapApi
    events.forEach((event) => {
      if (event.latitude === null || event.longitude === null) return

      const marker = document.createElement('button')
      marker.type = 'button'
      marker.className = 'event-place-marker'
      marker.innerHTML = `<i aria-hidden="true"></i><span></span>`
      marker.querySelector('span')!.textContent = event.title
      marker.setAttribute('aria-label', `행사 ${event.title} 상세 보기`)
      marker.addEventListener('click', () => {
        setSelectedEvent(event)
        setSelectedCell(null)
        setSelectedPlace(null)
      })

      const overlay = new maps.CustomOverlay({
        position: new maps.LatLng(event.latitude, event.longitude),
        content: marker,
        map,
        yAnchor: 1,
        zIndex: 5,
        clickable: true,
      })
      eventOverlaysRef.current.push(overlay)
    })
  }, [events, isMapReady, showEventLayer])

  const resetFilters = () => {
    setDraftFilters(initialMapFilters)
    setAppliedFilters(initialMapFilters)
    setShowGridLayer(true)
    setShowReportLayer(true)
    setShowCongestionLayer(true)
    setShowEventLayer(false)
  }

  const filteredReports = reports.filter((report) => (
    (!appliedFilters.region || report.location.toLowerCase().includes(appliedFilters.region.toLowerCase())) &&
    (!appliedFilters.riskLevel ||
      appliedFilters.riskLevel === allValue ||
      matchesRiskLevel(report.riskLevel, appliedFilters.riskLevel, report.riskScore))
  ))
  const plottableReports = filteredReports.filter(hasCoordinates)
  const uncheckedReportCount = plottableReports.filter(isNewReport).length
  const checkedReportCount = plottableReports.length - uncheckedReportCount

  const gridStats = useMemo(() => {
    if (gridRisks.length === 0) return null
    const peak = gridRisks.reduce((highest, risk) => (risk.score > highest.score ? risk : highest))
    return {
      cellCount: gridRisks.length,
      averageScore: Math.round(gridRisks.reduce((sum, risk) => sum + risk.score, 0) / gridRisks.length),
      peakScore: Math.round(peak.score),
      peakLabel: getRiskConfig(peak.congestionLevel, peak.score)?.label ?? '미분류',
    }
  }, [gridRisks])

  return (
    <section className="page-stack">
      <form
        className="risk-map-filter"
        onSubmit={(event) => {
          event.preventDefault()
          setAppliedFilters({
            ...draftFilters,
            region: draftFilters.region.trim(),
          })
        }}
      >
        <div className="risk-map-filter__controls">
          <div className="risk-map-filter__region">
            <LocationIcon />
            <input
              aria-label="지역 검색"
              type="search"
              value={draftFilters.region}
              onChange={(event) => setDraftFilters((current) => ({ ...current, region: event.target.value }))}
              placeholder="지역명을 입력하세요."
            />
          </div>
          <select
            aria-label="위험도 선택"
            className={!draftFilters.riskLevel ? 'select-placeholder' : undefined}
            value={draftFilters.riskLevel}
            onChange={(event) => setDraftFilters((current) => ({
              ...current,
              riskLevel: event.target.value as MapFilters['riskLevel'],
            }))}
          >
            <option value={unsetValue} disabled>위험도</option>
            <option value={allValue}>전체</option>
            {RISK_LEVEL_OPTIONS.map(({ value, label }) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
          <button type="button" className="secondary-button" onClick={resetFilters}>
            초기화
          </button>
          <button type="submit" className="primary-button">필터 적용</button>
        </div>
        <fieldset className="layer-options">
          <legend>레이어 선택</legend>
          <label>
            <input
              type="checkbox"
              checked={showGridLayer}
              onChange={() => setShowGridLayer((current) => !current)}
            />
            격자 위험도
          </label>
          <label>
            <input
              type="checkbox"
              checked={showReportLayer}
              onChange={() => setShowReportLayer((current) => !current)}
            />
            제보
          </label>
          <label>
            <input
              type="checkbox"
              checked={showCongestionLayer}
              onChange={() => setShowCongestionLayer((current) => !current)}
            />
            혼잡도 ({places.length})
          </label>
          <label>
            <input
              type="checkbox"
              checked={showEventLayer}
              onChange={() => setShowEventLayer((current) => !current)}
            />
            행사 ({events.length})
          </label>
        </fieldset>
      </form>

      <div className="risk-map-layout">
        <article className="admin-card">
          <div className="risk-map-canvas">
            <ReportMap
              className="map-placeholder"
              reports={showReportLayer ? filteredReports : []}
              onSelect={(report) => navigate(createReportDetailPath(report.id))}
              onMapReady={handleMapReady}
            />
            <div className="risk-map-legend" aria-label="위험도 범례">
              {RISK_LEVEL_ORDER.map((level) => (
                <span key={level}>
                  <i style={{ backgroundColor: RISK_LEVEL_CONFIG[level].markerColor }} aria-hidden="true" />
                  {RISK_LEVEL_CONFIG[level].label}
                </span>
              ))}
            </div>
          </div>
          {gridError && <p className="empty-state" role="alert">{gridError}</p>}
          {!isLoading && showReportLayer && plottableReports.length === 0 && (
            <p className="empty-state" role="status">지도에 표시할 좌표가 있는 제보가 없습니다.</p>
          )}
        </article>

        <aside className="map-stats">
          {selectedPlace ? (
            <article className="admin-card grid-trend-card">
              <div className="grid-trend-card__heading">
                <div>
                  <span>{PLACE_CATEGORY_LABELS[selectedPlace.category] ?? selectedPlace.category}</span>
                  <strong>{selectedPlace.placeName}</strong>
                </div>
                <button type="button" className="text-button" onClick={clearSelection}>닫기</button>
              </div>
              <p className="congestion-level-badge" style={{ backgroundColor: congestionConfig(selectedPlace.congestionLevel)?.color }}>
                {congestionConfig(selectedPlace.congestionLevel)?.label ?? selectedPlace.congestionLevel}
              </p>
              <dl className="grid-trend-card__meta">
                <div>
                  <dt>실시간 인구</dt>
                  <dd>
                    {selectedPlace.populationMin !== null && selectedPlace.populationMax !== null
                      ? `${selectedPlace.populationMin.toLocaleString()}~${selectedPlace.populationMax.toLocaleString()}명`
                      : '정보 없음'}
                  </dd>
                </div>
                <div>
                  <dt>관측 시각</dt>
                  <dd>{formatAdminDate(selectedPlace.observedAt)}</dd>
                </div>
              </dl>
              {selectedPlace.congestionMessage && (
                <p className="congestion-message break-text">{selectedPlace.congestionMessage}</p>
              )}
            </article>
          ) : selectedEvent ? (
            <article className="admin-card grid-trend-card">
              <div className="grid-trend-card__heading">
                <div>
                  <span>문화 행사 · {selectedEvent.areaName ?? '지역 정보 없음'}</span>
                  <strong className="break-text">{selectedEvent.title}</strong>
                </div>
                <button type="button" className="text-button" onClick={clearSelection}>닫기</button>
              </div>
              <dl className="grid-trend-card__meta">
                <div><dt>장소</dt><dd className="break-text">{selectedEvent.venueName ?? '정보 없음'}</dd></div>
                <div><dt>기간</dt><dd>{selectedEvent.eventPeriod ?? '정보 없음'}</dd></div>
                <div><dt>요금</dt><dd>{selectedEvent.paid === null ? '정보 없음' : selectedEvent.paid ? '유료' : '무료'}</dd></div>
              </dl>
              {selectedEvent.detailUrl && (
                <a className="text-button" href={selectedEvent.detailUrl} target="_blank" rel="noreferrer">
                  행사 상세 페이지 열기 ↗
                </a>
              )}
            </article>
          ) : selectedCell ? (
            <article className="admin-card grid-trend-card">
              <div className="grid-trend-card__heading">
                <div>
                  <span>선택한 격자 ({selectedCell.resolutionMeters}m)</span>
                  <strong>
                    {Math.round(selectedCell.score)}점
                    {' · '}
                    {getRiskConfig(selectedCell.congestionLevel, selectedCell.score)?.label ?? '미분류'}
                  </strong>
                </div>
                <button
                  type="button"
                  className="text-button"
                  onClick={clearSelection}
                >
                  닫기
                </button>
              </div>
              <dl className="grid-trend-card__meta">
                <div>
                  <dt>점수 출처</dt>
                  <dd>{selectedCell.observationStatus === 'REPORTED' ? '시민 제보' : '공공데이터'}</dd>
                </div>
                <div><dt>근거 수</dt><dd>{selectedCell.evidenceCount}건</dd></div>
                <div><dt>제보자 수</dt><dd>{selectedCell.distinctReporterCount}명</dd></div>
                <div><dt>신뢰도</dt><dd>{Math.round(selectedCell.confidenceScore * 100)}%</dd></div>
              </dl>
              {trendError ? (
                <p className="empty-state" role="alert">{trendError}</p>
              ) : trend === null ? (
                <p role="status">추이를 불러오는 중입니다...</p>
              ) : (
                <GridTrendChart points={trend} />
              )}
            </article>
          ) : (
            <>
              <article className="admin-card">
                <span>표시 중인 위험 격자</span>
                <strong>{gridStats ? `${gridStats.cellCount}칸` : '없음'}</strong>
              </article>
              <article className="admin-card">
                <span>최고 위험도</span>
                <strong>{gridStats ? `${gridStats.peakScore}점 · ${gridStats.peakLabel}` : '-'}</strong>
              </article>
              <article className="admin-card">
                <span>평균 위험도</span>
                <strong>{gridStats ? `${gridStats.averageScore}점` : '-'}</strong>
              </article>
              <article className="admin-card">
                <span>미확인 제보</span>
                <strong>{uncheckedReportCount}건</strong>
              </article>
              <article className="admin-card">
                <span>확인 제보</span>
                <strong>{checkedReportCount}건</strong>
              </article>
              <p className="map-stats__hint">격자를 클릭하면 위험도 추이, 장소·행사 마커를 클릭하면 상세 정보를 볼 수 있습니다.</p>
            </>
          )}
        </aside>
      </div>
    </section>
  )
}
