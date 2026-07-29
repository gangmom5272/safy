import { useEffect, useRef, useState } from 'react'
import { loadKakaoMaps, type KakaoCircle, type KakaoMap } from './kakaoMap'
import type { ReportLocation } from './reportTypes'

// 카카오맵은 숫자가 작을수록 확대된 상태다.
const MAP_LEVEL = 3
const MAP_MIN_LEVEL = 1
const MAP_MAX_LEVEL = 6
// 약 0.1m. 지도 중심 좌표는 픽셀을 거치며 미세하게 어긋나므로 이 이하는 움직이지 않은 것으로 본다.
const CENTER_EPSILON = 0.000001
// 카카오 기본 마커. Marker 객체는 좌표에 묶여 드래그 중 따라 움직이므로 같은 이미지를 화면 중앙에 고정해 쓴다.
const DEFAULT_MARKER_SRC = 'https://t1.daumcdn.net/mapjsapi/images/2x/marker.png'
const EARTH_RADIUS_M = 6371008.8

interface LocationMapProps {
  latitude: number
  longitude: number
  /** GPS 로 잡은 실제 위치. 허용 반경의 중심이다. */
  origin: ReportLocation
  radiusMeters: number
  onCenterChange: (location: ReportLocation) => void
  onClampedChange: (isClamped: boolean) => void
}

function toRadians(degrees: number) {
  return (degrees * Math.PI) / 180
}

function distanceInMeters(from: ReportLocation, to: ReportLocation) {
  const deltaLat = toRadians(to.latitude - from.latitude)
  const deltaLng = toRadians(to.longitude - from.longitude)
  const haversine =
    Math.sin(deltaLat / 2) ** 2 +
    Math.cos(toRadians(from.latitude)) *
      Math.cos(toRadians(to.latitude)) *
      Math.sin(deltaLng / 2) ** 2
  return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(haversine))
}

/** 1km 이내에서는 접평면 선형 보간으로 충분하다. 오차가 1m 아래다. */
function clampToRadius(origin: ReportLocation, target: ReportLocation, radiusMeters: number) {
  const distance = distanceInMeters(origin, target)
  if (distance <= radiusMeters) return null

  const ratio = radiusMeters / distance
  return {
    latitude: origin.latitude + (target.latitude - origin.latitude) * ratio,
    longitude: origin.longitude + (target.longitude - origin.longitude) * ratio,
  }
}

function isSamePlace(left: ReportLocation, right: ReportLocation) {
  return (
    Math.abs(left.latitude - right.latitude) < CENTER_EPSILON &&
    Math.abs(left.longitude - right.longitude) < CENTER_EPSILON
  )
}

export function LocationMap({
  latitude,
  longitude,
  origin,
  radiusMeters,
  onCenterChange,
  onClampedChange,
}: LocationMapProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const mapRef = useRef<KakaoMap | null>(null)
  const circleRef = useRef<KakaoCircle | null>(null)
  const centerRef = useRef<ReportLocation>({ latitude, longitude })
  const boundsRef = useRef({ origin, radiusMeters })
  const onCenterChangeRef = useRef(onCenterChange)
  const onClampedChangeRef = useRef(onClampedChange)
  const [hasFailed, setHasFailed] = useState(false)

  useEffect(() => {
    boundsRef.current = { origin, radiusMeters }
    onCenterChangeRef.current = onCenterChange
    onClampedChangeRef.current = onClampedChange
  }, [onCenterChange, onClampedChange, origin, radiusMeters])

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

        const bounds = boundsRef.current
        const circle = new maps.Circle({
          center: new maps.LatLng(bounds.origin.latitude, bounds.origin.longitude),
          radius: bounds.radiusMeters,
          strokeWeight: 2,
          strokeColor: '#004aad',
          strokeOpacity: 0.6,
          fillColor: '#004aad',
          fillOpacity: 0.06,
        })
        circle.setMap(map)
        circleRef.current = circle

        // idle 은 지도가 멈춘 뒤 한 번만 오므로 별도 디바운스가 필요 없다.
        const handleIdle = () => {
          const nextCenter = map.getCenter()
          const next = { latitude: nextCenter.getLat(), longitude: nextCenter.getLng() }

          // 지도 생성이나 외부 좌표 반영으로 발생한 idle 은 사용자가 움직인 것이 아니다.
          if (isSamePlace(centerRef.current, next)) {
            return
          }

          const current = boundsRef.current
          const clamped = clampToRadius(current.origin, next, current.radiusMeters)
          // 되돌리기 전에 기준을 먼저 옮겨야, setCenter 가 다시 발생시킨 idle 을 조작으로 보지 않는다.
          centerRef.current = clamped ?? next

          if (clamped) {
            map.setCenter(new maps.LatLng(clamped.latitude, clamped.longitude))
          }

          onClampedChangeRef.current(clamped !== null)
          onCenterChangeRef.current(centerRef.current)
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

  useEffect(() => {
    const circle = circleRef.current
    if (!circle) return

    circle.setRadius(radiusMeters)
    void loadKakaoMaps().then((maps) => {
      circle.setPosition(new maps.LatLng(origin.latitude, origin.longitude))
    })
  }, [origin, radiusMeters])

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
