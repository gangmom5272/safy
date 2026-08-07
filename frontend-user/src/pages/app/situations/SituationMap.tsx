import { useCallback, useEffect, useRef, useState } from 'react'
import {
  loadKakaoMaps,
  type KakaoCustomOverlay,
  type KakaoMap,
  type KakaoPolygon,
} from '../report-create/kakaoMap'
import {
  getSubcategoryLabel,
  reportTypeLabels,
  riskBadgeLabel,
  type Coordinates,
  type LiveReportSummary,
} from './types'
import {
  groupReportsByLocation,
  highestRiskLevel,
  RISK_MARKER_COLORS,
  riskMarkerColor,
  type ReportMarkerGroup,
} from './situationMapUtils'
import { normalizeRiskLevel } from './situationMapper'
import { getGridRisks, resolutionForLevel, snapBounds, type GridRisk } from './gridRiskApi'
import { MapLegendScale } from './MapLegend'
import './SituationMap.css'

// 카카오맵은 숫자가 작을수록 확대된 상태다.
const DEFAULT_LEVEL = 5
// 좌표가 없을 때의 기본 중심(서울시청). 측위 실패 시에도 지도는 띄운다.
const FALLBACK_CENTER: Coordinates = { latitude: 37.5665, longitude: 126.978 }
// 지도 이동·줌마다 바로 조회하지 않도록 계약 권고값(200~300ms)으로 디바운스한다.
const GRID_DEBOUNCE_MS = 250
// 현재 위치로 복귀할 때 허용할 배율 범위. 이 밖이면 DEFAULT_LEVEL 로 되돌린다 —
// 건물 하나만 보이거나 서울 전체가 보이는 상태로 돌아가면 내 위치를 알아볼 수 없다.
const RECENTER_MIN_LEVEL = 3
const RECENTER_MAX_LEVEL = 6

/** 같은 등급 안에서 점수로 채움 농도를 나눈다. 등급만 쓰면 45점과 60점이 같은 색이 된다. */
function gridFillOpacity(score: number) {
  const ratio = Math.min(Math.max(score, 0), 100) / 100
  return 0.16 + ratio * 0.38
}

interface SituationMapProps {
  /** 지도 중심. 지역 검색 전에는 현재 위치, 검색 후에는 지역 중심이다. */
  center: Coordinates | null
  /** 마커로 보여줄 제보 목록. 검색 전에는 전체 공개 피드, 검색 후에는 지역 피드다. */
  reports: LiveReportSummary[]
  /** 현재 위치 점 표시 여부. 지역 검색 전 화면에서만 켠다. */
  showCurrentLocation: boolean
  /**
   * 측위로 잡은 사용자 좌표. center 와 달리 지역을 검색해도 바뀌지 않으므로
   * "현재 위치로 이동" 버튼이 돌아갈 자리로 쓴다. 측위 전에는 null 이다.
   */
  currentLocation: Coordinates | null
  /**
   * 마커가 모두 보이도록 시야를 맞출지 여부. 지역 검색 후에만 켠다 —
   * 검색 전에 켜면 전체 제보 범위로 지도가 튀어 현재 위치 중심이 무너진다.
   */
  fitToReports: boolean
  statusMessage?: string
  onSelectReport: (reportId: string) => void
}

export function SituationMap({
  center,
  reports,
  showCurrentLocation,
  currentLocation,
  fitToReports,
  statusMessage,
  onSelectReport,
}: SituationMapProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const mapRef = useRef<KakaoMap | null>(null)
  // 지도 생성 시점의 중심. 이후 이동은 center 효과가 처리하므로 최초 값만 기억한다.
  const initialCenterRef = useRef<Coordinates>(center ?? FALLBACK_CENTER)
  const overlaysRef = useRef<KakaoCustomOverlay[]>([])
  const currentLocationOverlayRef = useRef<KakaoCustomOverlay | null>(null)
  const gridPolygonsRef = useRef<KakaoPolygon[]>([])
  const gridDebounceRef = useRef<number | null>(null)
  const gridAbortRef = useRef<AbortController | null>(null)
  const [isReady, setIsReady] = useState(false)
  const [hasFailed, setHasFailed] = useState(false)
  const [openGroup, setOpenGroup] = useState<ReportMarkerGroup | null>(null)
  const [gridRisks, setGridRisks] = useState<GridRisk[]>([])

  // 뷰포트 안 격자 위험도를 받아온다. bbox 는 격자 경계로 스냅해 3분 캐시가 듣게 한다.
  const loadGridRisks = useCallback(async () => {
    const map = mapRef.current
    if (!map) return

    const bounds = map.getBounds()
    const southWest = bounds.getSouthWest()
    const northEast = bounds.getNorthEast()
    const resolution = resolutionForLevel(map.getLevel())
    const snapped = snapBounds(
      {
        minLat: southWest.getLat(),
        maxLat: northEast.getLat(),
        minLng: southWest.getLng(),
        maxLng: northEast.getLng(),
      },
      resolution,
    )

    gridAbortRef.current?.abort()
    const controller = new AbortController()
    gridAbortRef.current = controller

    try {
      const risks = await getGridRisks(snapped, resolution, controller.signal)
      if (!controller.signal.aborted) setGridRisks(risks)
    } catch {
      // 격자 위험도는 배경 정보라 실패해도 지도와 제보 마커는 그대로 보여준다.
    }
  }, [])

  /**
   * 현재 위치로 지도 중심을 되돌린다. center prop 은 그대로이므로 상태만 바꿔서는
   * 아무 일도 일어나지 않는다 — 지도 인스턴스에 직접 setCenter 한다. 좌표는 이미
   * 받아둔 값을 쓰므로 누를 때마다 위치 권한을 다시 묻지 않는다.
   */
  const handleRecenter = useCallback(() => {
    const map = mapRef.current
    if (!map || !currentLocation) return

    void loadKakaoMaps().then((maps) => {
      const level = map.getLevel()
      if (level < RECENTER_MIN_LEVEL || level > RECENTER_MAX_LEVEL) map.setLevel(DEFAULT_LEVEL)
      map.setCenter(new maps.LatLng(currentLocation.latitude, currentLocation.longitude))
    })
  }, [currentLocation])

  // 지도 생성은 한 번만 한다.
  useEffect(() => {
    let isCurrent = true

    void loadKakaoMaps()
      .then((maps) => {
        const container = containerRef.current
        if (!isCurrent || !container || mapRef.current) return

        const initial = initialCenterRef.current
        const map = new maps.Map(container, {
          center: new maps.LatLng(initial.latitude, initial.longitude),
          level: DEFAULT_LEVEL,
        })
        mapRef.current = map
        // 이동·줌이 끝날 때마다 보이는 범위의 격자 위험도를 다시 받는다.
        maps.event.addListener(map, 'idle', () => {
          if (gridDebounceRef.current !== null) window.clearTimeout(gridDebounceRef.current)
          gridDebounceRef.current = window.setTimeout(() => {
            gridDebounceRef.current = null
            void loadGridRisks()
          }, GRID_DEBOUNCE_MS)
        })
        void loadGridRisks()
        setIsReady(true)
      })
      .catch(() => {
        if (isCurrent) setHasFailed(true)
      })

    return () => {
      isCurrent = false
      if (gridDebounceRef.current !== null) window.clearTimeout(gridDebounceRef.current)
      gridAbortRef.current?.abort()
    }
  }, [loadGridRisks])

  // 격자 위험도 폴리곤. 응답에 있는 칸만 그린다 — 없는 칸은 근거가 없는 칸이지
  // 안전한 칸이 아니므로 0점으로 채워 색칠하지 않는다.
  useEffect(() => {
    const map = mapRef.current
    if (!isReady || !map) return

    void loadKakaoMaps().then((maps) => {
      gridPolygonsRef.current.forEach((polygon) => polygon.setMap(null))
      gridPolygonsRef.current = []

      gridRisks.forEach((risk) => {
        const level = normalizeRiskLevel(risk.congestionLevel)
        const color = RISK_MARKER_COLORS[level]

        // 경계 좌표를 그대로 쓴다. gridCode 를 파싱해 역산하지 않는다.
        const polygon = new maps.Polygon({
          map,
          path: [
            new maps.LatLng(risk.southWestLatitude, risk.southWestLongitude),
            new maps.LatLng(risk.southWestLatitude, risk.northEastLongitude),
            new maps.LatLng(risk.northEastLatitude, risk.northEastLongitude),
            new maps.LatLng(risk.northEastLatitude, risk.southWestLongitude),
          ],
          strokeWeight: 1,
          strokeColor: color,
          strokeOpacity: 0.6,
          fillColor: color,
          fillOpacity: gridFillOpacity(risk.score),
          zIndex: 1,
        })
        gridPolygonsRef.current.push(polygon)
      })
    })
  }, [gridRisks, isReady])

  // 중심 이동 + 현재 위치 점 갱신. (0,0)은 좌표 없음 폴백이므로 이동하지 않는다.
  useEffect(() => {
    const map = mapRef.current
    if (!isReady || !map || !center) return
    if (center.latitude === 0 && center.longitude === 0) return

    void loadKakaoMaps().then((maps) => {
      const position = new maps.LatLng(center.latitude, center.longitude)
      // 탭 전환·레이아웃 변경으로 컨테이너 크기가 바뀌었으면 타일이 빈 채로 남는다.
      map.relayout()
      map.setCenter(position)

      currentLocationOverlayRef.current?.setMap(null)
      currentLocationOverlayRef.current = null

      if (showCurrentLocation) {
        const dot = document.createElement('span')
        dot.className = 'situation-map-current'
        dot.setAttribute('aria-label', '현재 위치')

        const overlay = new maps.CustomOverlay({ position, content: dot, zIndex: 2 })
        overlay.setMap(map)
        currentLocationOverlayRef.current = overlay
      }
    })
  }, [center, isReady, showCurrentLocation])

  // 제보 마커 갱신. 같은 위치의 제보는 개수 배지가 달린 마커 하나로 묶는다.
  useEffect(() => {
    const map = mapRef.current
    if (!isReady || !map) return

    setOpenGroup(null)

    void loadKakaoMaps().then((maps) => {
      overlaysRef.current.forEach((overlay) => overlay.setMap(null))
      overlaysRef.current = []

      const groups = groupReportsByLocation(reports)
      if (groups.length === 0) return

      groups.forEach((group) => {
        const riskLevel = highestRiskLevel(group.reports)
        const marker = document.createElement('button')
        marker.type = 'button'
        marker.className = 'situation-map-marker'
        marker.style.backgroundColor = riskMarkerColor(riskLevel)
        // 느낌표만 찍으면 무슨 상황인지 지도에서 알 수 없다. 한 건이면 세부 분류를
        // 이름으로 쓰고, 없을 때만 상위 분류로 내려간다. 여러 건은 개수를 유지한다.
        const single = group.reports[0]
        const singleLabel = getSubcategoryLabel(single.subcategory) ?? reportTypeLabels[single.type]
        marker.textContent = group.reports.length > 1 ? String(group.reports.length) : singleLabel
        // 색만으로는 미평가를 알 수 없으니 읽어 주는 이름에는 등급을 함께 넣는다.
        marker.setAttribute(
          'aria-label',
          group.reports.length > 1
            ? `같은 위치 제보 ${group.reports.length}건 · 위험도 ${riskBadgeLabel(riskLevel)} 목록 열기`
            : `제보 상세 보기: ${singleLabel} · 위험도 ${riskBadgeLabel(riskLevel)} · ${single.title}`,
        )
        marker.addEventListener('click', () => {
          if (group.reports.length === 1) {
            onSelectReport(group.reports[0].id)
          } else {
            setOpenGroup(group)
          }
        })

        const overlay = new maps.CustomOverlay({
          position: new maps.LatLng(group.latitude, group.longitude),
          content: marker,
          yAnchor: 1,
          zIndex: 3,
        })
        overlay.setMap(map)
        overlaysRef.current.push(overlay)
      })

      // 지역 검색 후에만 마커가 모두 보이도록 화면을 맞춘다. 한 곳뿐이면 그 위치로 이동만 한다.
      // 검색 전에는 현재 위치 중심을 유지한 채 마커만 얹는다.
      if (!fitToReports) return
      map.relayout()
      if (groups.length > 1) {
        const bounds = new maps.LatLngBounds()
        groups.forEach((group) => bounds.extend(new maps.LatLng(group.latitude, group.longitude)))
        map.setBounds(bounds)
      } else {
        map.setCenter(new maps.LatLng(groups[0].latitude, groups[0].longitude))
      }
    })
  }, [fitToReports, isReady, onSelectReport, reports])

  return (
    <div className="situation-map-frame">
      <div ref={containerRef} className="situation-map" role="application" aria-label="지역 상황 지도" />

      {/*
        하단 오버레이 스택. 토스트와 범례를 세로로 쌓아 서로 가리지 않게 한다. 측위
        실패·권한 거부 메시지는 사라지지 않고 폭도 거의 꽉 차므로, 겹치게 두면 범례가
        영구히 안 보인다. 범례는 설명할 지도가 실제로 떠 있을 때만 얹는다.
      */}
      <div className="situation-map-overlays">
        {hasFailed && <p className="situation-map-message">지도를 불러오지 못했습니다.</p>}
        {!hasFailed && statusMessage && <p className="situation-map-message">{statusMessage}</p>}
        {isReady && !hasFailed && (
          <button
            type="button"
            className="situation-map-recenter"
            disabled={!currentLocation}
            aria-label="현재 위치로 이동"
            title="현재 위치로 이동"
            onClick={handleRecenter}
          >
            <RecenterIcon />
          </button>
        )}
        {isReady && !hasFailed && <MapLegendScale />}
      </div>

      {openGroup && (
        <div className="situation-map-group" role="dialog" aria-label="같은 위치 제보 목록">
          <div className="situation-map-group__heading">
            <strong>이 위치의 제보 {openGroup.reports.length}건</strong>
            <button type="button" onClick={() => setOpenGroup(null)} aria-label="제보 목록 닫기">×</button>
          </div>
          <ul>
            {openGroup.reports.map((report) => (
              <li key={report.id}>
                <button type="button" onClick={() => onSelectReport(report.id)}>
                  <span
                    className="situation-map-group__risk"
                    style={{ backgroundColor: riskMarkerColor(report.riskLevel) }}
                  >
                    {riskBadgeLabel(report.riskLevel)}
                  </span>
                  <span className="situation-map-group__title">{report.title}</span>
                  <span className="situation-map-group__meta">
                    {getSubcategoryLabel(report.subcategory) ?? reportTypeLabels[report.type]}
                    {report.relativeTime ? ` · ${report.relativeTime}` : ''}
                  </span>
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}

/** 네이버·카카오 지도와 같은 조준점 아이콘. 아이콘만 있는 버튼이라 이름은 aria-label 이 준다. */
function RecenterIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
      <circle cx="12" cy="12" r="4" />
      <circle cx="12" cy="12" r="8" />
      <path d="M12 2v3M12 19v3M2 12h3M19 12h3" />
    </svg>
  )
}
