import { useEffect, useRef, useState } from 'react'
import { loadKakaoMaps, type KakaoMap, type KakaoMarker } from './kakaoMap'

// 카카오맵은 숫자가 작을수록 확대된 상태다.
const MAP_LEVEL = 4
const MAP_MIN_LEVEL = 2
const MAP_MAX_LEVEL = 6

interface LocationMapProps {
  latitude: number
  longitude: number
}

export function LocationMap({ latitude, longitude }: LocationMapProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const mapRef = useRef<KakaoMap | null>(null)
  const markerRef = useRef<KakaoMarker | null>(null)
  const [hasFailed, setHasFailed] = useState(false)

  useEffect(() => {
    let isCurrent = true

    void loadKakaoMaps()
      .then((maps) => {
        const container = containerRef.current

        if (!isCurrent || !container) {
          return
        }

        const center = new maps.LatLng(latitude, longitude)

        if (mapRef.current && markerRef.current) {
          mapRef.current.setCenter(center)
          markerRef.current.setPosition(center)
          return
        }

        const map = new maps.Map(container, { center, level: MAP_LEVEL })
        map.setMinLevel(MAP_MIN_LEVEL)
        map.setMaxLevel(MAP_MAX_LEVEL)
        mapRef.current = map
        markerRef.current = new maps.Marker({ position: center, map })
        setHasFailed(false)
      })
      .catch(() => {
        if (isCurrent) {
          setHasFailed(true)
        }
      })

    return () => {
      isCurrent = false
    }
  }, [latitude, longitude])

  return (
    <div ref={containerRef} className="report-mini-map">
      {hasFailed ? <p className="report-mini-map-fallback">지도를 불러오지 못했습니다.</p> : null}
    </div>
  )
}
