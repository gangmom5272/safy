import { useState } from 'react'
import { ErrorState, LoadingSkeleton } from './SituationStates'
import { riskLevelLabels, type AreaMapData, type ReportMarker } from './types'

type AreaMapProps = {
  mapData?: AreaMapData
  isLoading: boolean
  error: unknown
}

export function AreaMap({ mapData, isLoading, error }: AreaMapProps) {
  const [selectedMarker, setSelectedMarker] = useState<ReportMarker | null>(null)

  if (isLoading && !mapData) {
    return <LoadingSkeleton />
  }

  if (error && !mapData) {
    return <ErrorState message="지도 데이터를 불러오지 못했습니다." />
  }

  if (!mapData) {
    return null
  }

  return (
    <section className="area-map" aria-labelledby="area-map-title">
      <div className="section-title-row compact">
        <div>
          <h2 id="area-map-title">지도 데이터</h2>
          <p>
            중심 좌표 {mapData.center.latitude.toFixed(4)}, {mapData.center.longitude.toFixed(4)}
          </p>
        </div>
      </div>

      <div className="map-data-summary">
        <span>검색 위치</span>
        <span>혼잡 구역 {mapData.zones.length}개</span>
        <span>제보 마커 {mapData.reportMarkers.length}개</span>
      </div>

      <div className="map-placeholder" role="img" aria-label="추후 지도 SDK 연결 영역">
        <span className="map-center-label">검색 위치</span>
        {mapData.zones.map((zone, index) => (
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
            {zone.name ?? '혼잡 구역'} {riskLevelLabels[zone.level]}
          </span>
        ))}

        {mapData.reportMarkers.map((marker, index) => (
          <button
            key={marker.reportId}
            className={`map-marker risk-${marker.riskLevel}`}
            type="button"
            style={{
              left: `${34 + index * 11}%`,
              top: `${42 + index * 7}%`,
            }}
            aria-label={`${marker.locationName} 제보 마커`}
            onClick={() => setSelectedMarker(marker)}
          >
            {index + 1}
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

      <ul className="map-marker-list" aria-label="지도 제보 마커 목록">
        <li>
          <strong>범례</strong>
          <span>매우 혼잡 · 혼잡 · 보통 · 여유</span>
        </li>
        {mapData.reportMarkers.map((marker) => (
          <li key={marker.reportId}>
            <strong>{marker.locationName}</strong>
            <span>{riskLevelLabels[marker.riskLevel]}</span>
          </li>
        ))}
      </ul>
    </section>
  )
}
