import { useEffect, useRef, useState, type CSSProperties } from 'react'
import type { AdminReport } from '../../types/admin'
import { RISK_LEVEL_CONFIG, RISK_LEVEL_ORDER } from '../../utils/adminRisk'
import { VERIFICATION_CONFIG } from '../../utils/adminVerification'
import { loadKakaoMaps, type KakaoCustomOverlay, type KakaoMap } from '../../services/kakaoMap'
import { hasCoordinates } from '../../utils/reportGeo'
import { createReportInfoWindow, createReportPin, PIN_HEIGHT } from './reportMapMarkers'

interface ReportMapProps {
  reports: AdminReport[]
  className?: string
  /** 신규/확인 핀과 위험도 색을 설명하는 범례를 지도 안에 표시한다. */
  showLegend?: boolean
  /** 지도를 클릭했을 때 이동시킬 화면이 있으면 넘긴다. */
  onSelect?: (report: AdminReport) => void
}

// 표시할 제보가 없을 때 보여줄 기본 위치(서울시청).
const DEFAULT_CENTER = { latitude: 37.5665, longitude: 126.978 }

export function ReportMap({ reports, className = '', showLegend = false, onSelect }: ReportMapProps) {
  const containerRef = useRef<HTMLDivElement | null>(null)
  const mapRef = useRef<KakaoMap | null>(null)
  const overlaysRef = useRef<KakaoCustomOverlay[]>([])
  const infoOverlayRef = useRef<KakaoCustomOverlay | null>(null)
  // 실시간 이벤트로 목록이 갱신될 때마다 시야를 되돌리면 담당자가 확대·이동한 화면이 초기화된다.
  const hasFitBoundsRef = useRef(false)
  const [loadError, setLoadError] = useState('')

  // 최신 콜백을 참조해 지도를 다시 만들지 않고도 클릭 동작을 갱신한다.
  const onSelectRef = useRef(onSelect)
  useEffect(() => {
    onSelectRef.current = onSelect
  })

  useEffect(() => {
    let active = true

    void loadKakaoMaps()
      .then((maps) => {
        if (!active || !containerRef.current) return
        if (!mapRef.current) {
          mapRef.current = new maps.Map(containerRef.current, {
            center: new maps.LatLng(DEFAULT_CENTER.latitude, DEFAULT_CENTER.longitude),
            level: 8,
          })
        }

        const map = mapRef.current
        const closeInfoWindow = () => {
          infoOverlayRef.current?.setMap(null)
          infoOverlayRef.current = null
        }

        closeInfoWindow()
        overlaysRef.current.forEach((overlay) => overlay.setMap(null))
        overlaysRef.current = []

        const plotted = reports.filter(hasCoordinates)
        if (import.meta.env.DEV && plotted.length !== reports.length) {
          const skipped = reports.filter((report) => !hasCoordinates(report)).map((report) => report.id)
          console.warn('[ReportMap] 좌표가 없거나 잘못돼 지도에서 제외한 제보:', skipped)
        }

        const bounds = new maps.LatLngBounds()

        plotted.forEach((report) => {
          const position = new maps.LatLng(report.latitude, report.longitude)

          const openInfoWindow = () => {
            closeInfoWindow()
            const content = createReportInfoWindow(report, {
              onDetail: () => onSelectRef.current?.(report),
              onClose: closeInfoWindow,
            })
            infoOverlayRef.current = new maps.CustomOverlay({
              position,
              content,
              map,
              xAnchor: 0.5,
              yAnchor: 1,
              zIndex: 20,
              clickable: true,
            })
          }

          const pin = createReportPin(report, openInfoWindow)
          const overlay = new maps.CustomOverlay({
            position,
            content: pin,
            map,
            xAnchor: 0.5,
            yAnchor: 1,
            clickable: true,
          })
          overlaysRef.current.push(overlay)
          bounds.extend(position)
        })

        // 제보가 있으면 처음 한 번만 전부 보이도록 맞추고, 이후에는 담당자의 시야를 유지한다.
        if (!bounds.isEmpty() && !hasFitBoundsRef.current) {
          map.setBounds(bounds)
          hasFitBoundsRef.current = true
        }
        map.relayout()
      })
      .catch((error: unknown) => {
        if (active) setLoadError(error instanceof Error ? error.message : '지도를 불러오지 못했습니다.')
      })

    return () => { active = false }
  }, [reports])

  useEffect(() => () => {
    infoOverlayRef.current?.setMap(null)
    infoOverlayRef.current = null
    overlaysRef.current.forEach((overlay) => overlay.setMap(null))
    overlaysRef.current = []
    mapRef.current = null
  }, [])

  if (loadError) {
    return (
      <div className={`report-map report-map--error ${className}`.trim()} role="alert">
        <strong>{loadError}</strong>
        <span>VITE_KAKAO_MAP_JS_KEY 설정을 확인해 주세요.</span>
      </div>
    )
  }

  return (
    <div className={`report-map ${className}`.trim()} style={{ '--pin-height': `${PIN_HEIGHT}px` } as CSSProperties}>
      <div ref={containerRef} className="report-map__canvas" role="application" aria-label="제보 위치 지도" />
      {showLegend && (
        <div className="report-map__legend">
          <span>
            <i className="report-map__legend-pin" style={{ color: VERIFICATION_CONFIG.NEW.pinColor }} aria-hidden="true" />
            신규 제보
          </span>
          <span>
            <i className="report-map__legend-pin" style={{ color: VERIFICATION_CONFIG.CHECKED.pinColor }} aria-hidden="true" />
            확인한 제보
          </span>
          <span className="report-map__legend-risk">
            내부 색상 · 위험도
            {RISK_LEVEL_ORDER.map((level) => (
              <i
                key={level}
                style={{ backgroundColor: RISK_LEVEL_CONFIG[level].markerColor }}
                title={RISK_LEVEL_CONFIG[level].label}
                aria-hidden="true"
              />
            ))}
          </span>
        </div>
      )}
    </div>
  )
}
