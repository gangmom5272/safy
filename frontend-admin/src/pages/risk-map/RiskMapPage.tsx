import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import {
  adminReports,
  dashboardStats,
  webrtcConnections,
} from '../../mocks/adminMockData'
import type { AdminReport, RiskLevel } from '../../types/admin'
import { matchesRiskLevel, RISK_LEVEL_CONFIG, RISK_LEVEL_OPTIONS, RISK_LEVEL_ORDER } from '../../utils/adminRisk'
import { ReportMap } from '../../components/admin/ReportMap'
import { hasCoordinates } from '../../utils/reportGeo'
import { createReportDetailPath } from '../../router/paths'
import { getDashboardReports } from '../reports/reportApi'

const allValue = 'all'
const unsetValue = ''
const mapLayers = ['Heatmap', 'CCTV', 'WebRTC', '제보', '위험구역'] as const

interface MapFilters {
  region: string
  riskLevel: RiskLevel | typeof allValue | typeof unsetValue
}

const initialMapFilters: MapFilters = {
  region: '',
  riskLevel: unsetValue,
}

function LocationIcon() {
  return (
    <svg className="monitoring-filter__location-icon" viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false">
      <path d="M20 10c0 5-8 11-8 11S4 15 4 10a8 8 0 1 1 16 0Z" />
      <circle cx="12" cy="10" r="2.5" />
    </svg>
  )
}

function RefreshIcon() {
  return (
    <svg className="filter-reset-icon" viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false">
      <path d="M3 12a9 9 0 0 1 15.3-6.4L21 8M21 3v5h-5" />
      <path d="M21 12a9 9 0 0 1-15.3 6.4L3 16M3 21v-5h5" />
    </svg>
  )
}

export function RiskMapPage() {
  const navigate = useNavigate()
  const [draftFilters, setDraftFilters] = useState<MapFilters>(initialMapFilters)
  const [appliedFilters, setAppliedFilters] = useState<MapFilters>(initialMapFilters)
  const [activeLayers, setActiveLayers] = useState<string[]>(['Heatmap', '제보'])
  const [reports, setReports] = useState<AdminReport[]>([])
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    let active = true

    getDashboardReports(0, 100)
      .then((loaded) => { if (active) setReports(loaded) })
      .catch(() => undefined)
      .finally(() => { if (active) setIsLoading(false) })

    return () => { active = false }
  }, [])

  const resetFilters = () => {
    setDraftFilters(initialMapFilters)
    setAppliedFilters(initialMapFilters)
    setActiveLayers(['Heatmap', '제보'])
  }

  const toggleLayer = (layer: string) => {
    setActiveLayers((currentLayers) =>
      currentLayers.includes(layer)
        ? currentLayers.filter((currentLayer) => currentLayer !== layer)
        : [...currentLayers, layer],
    )
  }

  const filteredReports = reports.filter((report) => (
    (!appliedFilters.region || report.location.toLowerCase().includes(appliedFilters.region.toLowerCase())) &&
    (!appliedFilters.riskLevel ||
      appliedFilters.riskLevel === allValue ||
      matchesRiskLevel(report.riskLevel, appliedFilters.riskLevel, report.riskScore))
  ))
  const plottableCount = filteredReports.filter(hasCoordinates).length

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
          <input
            aria-label="현재 데이터에서 지원하지 않는 날짜 필터입니다"
            title="현재 데이터에서 지원하지 않는 필터입니다"
            type="date"
            disabled
          />
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
            <input
              aria-label="현재 데이터에서 지원하지 않는 행사 필터입니다"
              title="현재 데이터에서 지원하지 않는 필터입니다"
              type="text"
              placeholder="행사명"
              disabled
            />
          <button type="button" className="secondary-button" onClick={resetFilters}>
            초기화
          </button>
          <button
            type="button"
            className="secondary-button filter-reset-button"
            onClick={() => setAppliedFilters({ ...draftFilters, region: draftFilters.region.trim() })}
            aria-label="위험도 지도 새로고침"
            title="새로고침"
          >
            <RefreshIcon />
          </button>
          <button type="submit" className="primary-button">필터 적용</button>
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
      </form>

      <div className="risk-map-layout">
        <article className="admin-card">
          <div className="risk-map-canvas">
            <div className="active-layer-list">
              활성 레이어: {activeLayers.length > 0 ? activeLayers.join(', ') : '없음'}
            </div>
            <ReportMap
              className="map-placeholder"
              reports={filteredReports}
              onSelect={(report) => navigate(createReportDetailPath(report.id))}
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
          {!isLoading && plottableCount === 0 && (
            <p className="empty-state" role="status">지도에 표시할 좌표가 있는 제보가 없습니다.</p>
          )}
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
