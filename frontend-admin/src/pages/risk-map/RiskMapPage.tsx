import { useState } from 'react'
import {
  adminReports,
  dashboardStats,
  riskMarkers,
  webrtcConnections,
} from '../../mocks/adminMockData'
import type { RiskLevel } from '../../types/admin'
import { AdminSection } from '../../components/admin/AdminSection'
import { getRiskConfig, matchesRiskLevel, RISK_LEVEL_CONFIG, RISK_LEVEL_OPTIONS, RISK_LEVEL_ORDER } from '../../utils/adminRisk'

const allValue = 'all'
const mapLayers = ['Heatmap', 'CCTV', 'WebRTC', '제보', '위험구역'] as const

export function RiskMapPage() {
  const [region, setRegion] = useState('')
  const [riskLevel, setRiskLevel] = useState<RiskLevel | typeof allValue>(allValue)
  const [selectedDate, setSelectedDate] = useState('2026-07-23')
  const [eventFilter, setEventFilter] = useState('')
  const [activeLayers, setActiveLayers] = useState<string[]>(['Heatmap', '제보'])

  const resetFilters = () => {
    setRegion('')
    setRiskLevel(allValue)
    setSelectedDate('2026-07-23')
    setEventFilter('')
    setActiveLayers(['Heatmap', '제보'])
  }

  const toggleLayer = (layer: string) => {
    setActiveLayers((currentLayers) =>
      currentLayers.includes(layer)
        ? currentLayers.filter((currentLayer) => currentLayer !== layer)
        : [...currentLayers, layer],
    )
  }

  const filteredMarkers = riskMarkers.filter((marker) => (
    riskLevel === allValue || matchesRiskLevel(marker.riskLevel, riskLevel)
  ))

  return (
    <section className="page-stack">
      <AdminSection title="위험도 지도" action={<button type="button" className="secondary-button">새로고침</button>}>
        <div className="filter-grid">
          <label>
            지역 검색
            <input
              type="search"
              value={region}
              onChange={(event) => setRegion(event.target.value)}
              placeholder="지역명을 입력하세요"
            />
          </label>
          <label>
            날짜
            <input
              type="date"
              value={selectedDate}
              onChange={(event) => setSelectedDate(event.target.value)}
            />
          </label>
          <label>
            위험도
            <select
              value={riskLevel}
              onChange={(event) => setRiskLevel(event.target.value as RiskLevel | typeof allValue)}
            >
              <option value={allValue}>전체</option>
              {RISK_LEVEL_OPTIONS.map(({ value, label }) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </label>
          <label>
            행사 필터
            <input
              type="text"
              value={eventFilter}
              onChange={(event) => setEventFilter(event.target.value)}
              placeholder="행사명 placeholder"
            />
          </label>
          <button type="button" className="secondary-button" onClick={resetFilters}>
            필터 초기화
          </button>
        </div>
        <fieldset className="layer-options">
          <legend>레이어 선택</legend>
          {mapLayers.map((layer) => (
            <label key={layer}>
              <input
                type="checkbox"
                checked={activeLayers.includes(layer)}
                onChange={() => toggleLayer(layer)}
              />
              {layer}
            </label>
          ))}
        </fieldset>
      </AdminSection>

      <div className="risk-map-layout">
        <article className="admin-card">
          <div className="map-placeholder">
            <div className="active-layer-list">
              활성 레이어: {activeLayers.length > 0 ? activeLayers.join(', ') : '없음'}
            </div>
            {filteredMarkers.map((marker) => (
              <span
                key={marker.id}
                className="map-marker"
                style={{ top: `${marker.top}%`, left: `${marker.left}%`, backgroundColor: getRiskConfig(marker.riskLevel)?.markerColor ?? '#64748B', color: getRiskConfig(marker.riskLevel)?.markerTextColor ?? '#FFFFFF' }}
              >
                {marker.location}
              </span>
            ))}
            <div className="risk-map-legend" aria-label="위험도 범례">
              {RISK_LEVEL_ORDER.map((level) => (
                <span key={level}>
                  <i style={{ backgroundColor: RISK_LEVEL_CONFIG[level].markerColor }} aria-hidden="true" />
                  {RISK_LEVEL_CONFIG[level].label}
                </span>
              ))}
            </div>
          </div>
        </article>

        <aside className="map-stats">
          <article className="admin-card">
            <span>평균 위험도</span>
            <strong>68</strong>
          </article>
          <article className="admin-card">
            <span>전체 제보 수</span>
            <strong>{adminReports.length}</strong>
          </article>
          <article className="admin-card">
            <span>AI 감지 수</span>
            <strong>{adminReports.filter((report) => report.aiAnalysisStatus === 'detected').length}</strong>
          </article>
          <article className="admin-card">
            <span>실시간 WebRTC 연결 수</span>
            <strong>{dashboardStats.webrtcConnections}</strong>
          </article>
          <article className="admin-card">
            <span>전체 연결 데이터</span>
            <strong>{webrtcConnections.length}</strong>
          </article>
        </aside>
      </div>
    </section>
  )
}
