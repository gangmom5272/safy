import { useEffect, useRef, useState } from 'react'
import type { AdminReport } from '../../types/admin'
import { getRiskConfig } from '../../utils/adminRisk'
import { loadKakaoMaps, type KakaoCustomOverlay, type KakaoMap } from '../../services/kakaoMap'
import { hasCoordinates } from '../../utils/reportGeo'

interface ReportMapProps {
  reports: AdminReport[]
  className?: string
  /** 지도를 클릭했을 때 이동시킬 화면이 있으면 넘긴다. */
  onSelect?: (report: AdminReport) => void
}

// 표시할 제보가 없을 때 보여줄 기본 위치(서울시청).
const DEFAULT_CENTER = { latitude: 37.5665, longitude: 126.978 }

export function ReportMap({ reports, className = '', onSelect }: ReportMapProps) {
  const containerRef = useRef<HTMLDivElement | null>(null)
  const mapRef = useRef<KakaoMap | null>(null)
  const overlaysRef = useRef<KakaoCustomOverlay[]>([])
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
        overlaysRef.current.forEach((overlay) => overlay.setMap(null))
        overlaysRef.current = []

        const plotted = reports.filter(hasCoordinates)
        const bounds = new maps.LatLngBounds()

        plotted.forEach((report) => {
          const position = new maps.LatLng(report.latitude as number, report.longitude as number)
          const config = getRiskConfig(report.riskLevel, report.riskScore)

          const marker = document.createElement('button')
          marker.type = 'button'
          marker.className = 'map-marker map-marker--overlay'
          marker.textContent = report.location
          marker.style.backgroundColor = config?.markerColor ?? '#64748B'
          marker.style.color = config?.markerTextColor ?? '#FFFFFF'
          marker.addEventListener('click', () => onSelectRef.current?.(report))

          const overlay = new maps.CustomOverlay({ position, content: marker, map, yAnchor: 1, clickable: true })
          overlaysRef.current.push(overlay)
          bounds.extend(position)
        })

        // 제보가 있으면 전부 보이도록 맞추고, 없으면 기본 위치를 유지한다.
        if (!bounds.isEmpty()) map.setBounds(bounds)
        map.relayout()
      })
      .catch((error: unknown) => {
        if (active) setLoadError(error instanceof Error ? error.message : '지도를 불러오지 못했습니다.')
      })

    return () => { active = false }
  }, [reports])

  useEffect(() => () => {
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

  return <div ref={containerRef} className={`report-map ${className}`.trim()} role="application" aria-label="제보 위치 지도" />
}
