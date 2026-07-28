import { useEffect, useRef, useState } from 'react'
import { loadKakaoMaps, type KakaoMap } from './kakaoMap'
import type { ReportLocation } from './reportTypes'

// 카카오맵은 숫자가 작을수록 확대된 상태다.
const MAP_LEVEL = 3
const MAP_MIN_LEVEL = 1
const MAP_MAX_LEVEL = 6
// 약 0.1m. 지도 중심 좌표는 픽셀을 거치며 미세하게 어긋나므로 이 이하는 움직이지 않은 것으로 본다.
const CENTER_EPSILON = 0.000001
// 카카오 기본 마커. Marker 객체는 좌표에 묶여 드래그 중 따라 움직이므로 같은 이미지를 화면 중앙에 고정해 쓴다.
const DEFAULT_MARKER_SRC = 'https://t1.daumcdn.net/mapjsapi/images/2x/marker.png'

interface LocationMapProps {
  latitude: number
  longitude: number
  onCenterChange: (location: ReportLocation) => void
}

function isSamePlace(left: ReportLocation, right: ReportLocation) {
  return (
    Math.abs(left.latitude - right.latitude) < CENTER_EPSILON &&
    Math.abs(left.longitude - right.longitude) < CENTER_EPSILON
  )
}

export function LocationMap({ latitude, longitude, onCenterChange }: LocationMapProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const mapRef = useRef<KakaoMap | null>(null)
  const centerRef = useRef<ReportLocation>({ latitude, longitude })
  const onCenterChangeRef = useRef(onCenterChange)
  const [hasFailed, setHasFailed] = useState(false)

  useEffect(() => {
    onCenterChangeRef.current = onCenterChange
  }, [onCenterChange])

  useEffect(() => {
    let isCurrent = true
    let removeIdleListener: (() => void) | null = null

    void loadKakaoMaps()
      .then((maps) => {
        const container = containerRef.current

        if (!isCurrent || !container) {
          return
        }

        const initialCenter = centerRef.current
        const map = new maps.Map(container, {
          center: new maps.LatLng(initialCenter.latitude, initialCenter.longitude),
          level: MAP_LEVEL,
        })
        map.setMinLevel(MAP_MIN_LEVEL)
        map.setMaxLevel(MAP_MAX_LEVEL)
        mapRef.current = map

        // idle 은 지도가 멈춘 뒤 한 번만 오므로 별도 디바운스가 필요 없다.
        const handleIdle = () => {
          const nextCenter = map.getCenter()
          const next = { latitude: nextCenter.getLat(), longitude: nextCenter.getLng() }

          // 지도 생성이나 외부 좌표 반영으로 발생한 idle 은 사용자가 움직인 것이 아니다.
          if (isSamePlace(centerRef.current, next)) {
            return
          }

          centerRef.current = next
          onCenterChangeRef.current(next)
        }

        maps.event.addListener(map, 'idle', handleIdle)
        removeIdleListener = () => maps.event.removeListener(map, 'idle', handleIdle)
      })
      .catch(() => {
        if (isCurrent) {
          setHasFailed(true)
        }
      })

    return () => {
      isCurrent = false
      removeIdleListener?.()
    }
  }, [])

  // 현위치 재확인처럼 밖에서 좌표가 바뀐 경우에만 지도를 옮긴다.
  useEffect(() => {
    const map = mapRef.current

    if (!map || isSamePlace(centerRef.current, { latitude, longitude })) {
      return
    }

    centerRef.current = { latitude, longitude }
    void loadKakaoMaps().then((maps) => map.setCenter(new maps.LatLng(latitude, longitude)))
  }, [latitude, longitude])

  return (
    <div className="report-location-map-frame">
      <div ref={containerRef} className="report-location-map" />
      {hasFailed ? (
        <p className="report-location-map-fallback">지도를 불러오지 못했습니다.</p>
      ) : (
        <img className="report-location-pin" src={DEFAULT_MARKER_SRC} alt="" aria-hidden="true" />
      )}
    </div>
  )
}
