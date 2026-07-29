import { useState } from 'react'
import { useMapGestures } from '../../../components/maps/useMapGestures'
import { ErrorState, LoadingSkeleton } from './SituationStates'
import { riskLevelLabels, type AreaMapData, type ReportMarker } from './types'

type AreaMapProps = {
  mapData?: AreaMapData
  isLoading: boolean
  error: unknown
  variant?: 'default' | 'report-detail'
  locationName?: string
  showTitle?: boolean
}

export function AreaMap({ mapData, isLoading, error, variant = 'default', locationName, showTitle = true }: AreaMapProps) {
  const [selectedMarker, setSelectedMarker] = useState<ReportMarker | null>(null)
  const mapGestures = useMapGestures(mapData ? `${mapData.center.latitude}:${mapData.center.longitude}` : 'empty')

  if (isLoading && !mapData) {
    return <LoadingSkeleton />
  }

  if (error && !mapData) {
    return <ErrorState message="지도 데이터를 불러오지 못했습니다." />
  }

  if (!mapData) {
    return null
  }

  const isReportDetail = variant === 'report-detail'
  const distanceMeters = getDistanceToNearestZone(mapData)

  return (
    <section className={isReportDetail ? 'area-map report-detail-map' : 'area-map'} aria-labelledby={isReportDetail && showTitle ? 'area-map-title' : undefined} aria-label={isReportDetail && !showTitle ? '주변 위험 지도 콘텐츠' : !isReportDetail ? '위험 지역 지도 콘텐츠' : undefined}>
      {isReportDetail && showTitle ? <div className="section-title-row compact">
        <div>
          <h2 id="area-map-title">주변 위험 지도</h2>
        </div>
      </div> : null}

      <div className="map-data-summary">
        <><span><small>검색 위치</small><strong>{locationName || mapData.reportMarkers[0]?.locationName || '선택 지역'}</strong></span><span><small>위험 지역</small><strong>{mapData.zones.length}곳</strong></span><span><small>실시간 제보</small><strong>{mapData.status?.liveReportCount ?? mapData.reportMarkers.length}건</strong></span></>
      </div>

      <div className="map-placeholder map-gesture-viewport" role="group" aria-label="지역 상황 지도" {...mapGestures.viewportProps}>
        <div className="map-placeholder-content map-gesture-content" style={mapGestures.contentStyle}>
          {isReportDetail ? <><span className="map-block block-a"/><span className="map-block block-b"/><span className="map-block block-c"/><span className="map-park">공원</span><span className="map-road road-a"/><span className="map-road road-b"/><span className="current-location-pin"><i/><b>현재 위치</b></span></> : <span className="map-center-label">검색 위치</span>}
          {!isReportDetail && mapData.zones.map((zone, index) => (
            <span
              key={zone.id}
              className={`map-zone risk-${zone.level}`}
              style={{
                width: `${28 + Math.min(index * 8, 16)}%`,
                height: `${28 + Math.min(index * 8, 16)}%`,
                left: `${16 + index * 24}%`,
                top: `${18 + index * 16}%`,
              }}
            >
              {zone.name ?? riskLevelLabels[zone.level]}
            </span>
          ))}

          {mapData.reportMarkers.map((marker, index) => (
            <button
              key={marker.reportId}
              className={`map-marker risk-${marker.riskLevel}`}
              type="button"
              style={isReportDetail ? { left: `${68 + index * 6}%`, top: `${38 + index * 7}%` } : { left: `${34 + index * 11}%`, top: `${42 + index * 7}%` }}
              aria-label={`${marker.locationName} 제보 마커`}
              onClick={() => setSelectedMarker(marker)}
            >
              {isReportDetail ? <><span aria-hidden="true">!</span><b>위험 위치</b></> : index + 1}
            </button>
          ))}

          {selectedMarker ? (
            <div className="map-marker-popover">
              <strong>{selectedMarker.locationName}</strong>
              <span>
                {selectedMarker.latitude.toFixed(4)}, {selectedMarker.longitude.toFixed(4)}
              </span>
              <span>{riskLevelLabels[selectedMarker.riskLevel]}</span>
            </div>
          ) : null}
        </div>
      </div>

      {isReportDetail ? <div className="map-distance-summary"><small>현재 위치 기준</small><strong>{formatDistance(distanceMeters)}</strong><span>{formatWalkingTime(distanceMeters)}</span></div> : null}

    </section>
  )
}

function getDistanceToNearestZone(mapData: AreaMapData) {
  const target = mapData.zones[0]?.coordinates[0]
  if (!target) return null

  const earthRadius = 6_371_000
  const toRadians = (degrees: number) => degrees * Math.PI / 180
  const latitudeDelta = toRadians(target.latitude - mapData.center.latitude)
  const longitudeDelta = toRadians(target.longitude - mapData.center.longitude)
  const startLatitude = toRadians(mapData.center.latitude)
  const targetLatitude = toRadians(target.latitude)
  const value = Math.sin(latitudeDelta / 2) ** 2 + Math.cos(startLatitude) * Math.cos(targetLatitude) * Math.sin(longitudeDelta / 2) ** 2
  return Math.round(earthRadius * 2 * Math.atan2(Math.sqrt(value), Math.sqrt(1 - value)))
}

function formatDistance(distanceMeters: number | null) {
  if (distanceMeters == null) return '위험 지역까지 거리 정보 준비 중'
  if (distanceMeters < 10) return '위험 지역까지 약 10m 이내'
  if (distanceMeters < 1_000) return `위험 지역까지 약 ${distanceMeters}m`
  return `위험 지역까지 약 ${(distanceMeters / 1_000).toFixed(1)}km`
}

function formatWalkingTime(distanceMeters: number | null) {
  if (distanceMeters == null) return '도보 시간 정보 준비 중'
  if (distanceMeters < 80) return '도보 약 1분 이내'
  return `도보 약 ${Math.ceil(distanceMeters / 80)}분`
}
