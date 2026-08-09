import { useCallback } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { AreaStatusSummary } from './AreaStatusSummary'
import { LocationSearchBar } from './LocationSearchBar'
import { MapLegendNotes } from './MapLegend'
import { ReportFilterSheet } from './ReportFilterSheet'
import { SelectedAreaHeader } from './SelectedAreaHeader'
import { SituationHeader } from './SituationHeader'
import { SituationMap } from './SituationMap'
import { useSituationCheck } from './useSituationCheck'
import { useGeolocation } from '../report-create/useGeolocation'
import './SituationsPage.css'

export function SituationsPage() {
  const situation = useSituationCheck()
  const navigate = useNavigate()
  const location = useLocation()
  // 지도 중심은 수십 m 오차가 문제되지 않는다. 정밀 측위를 끄면 훨씬 빨리 끝나고,
  // 훅이 마지막 좌표를 캐시해 두므로 다시 들어올 때 기다리지 않는다.
  const geolocation = useGeolocation({ highAccuracy: false })

  const selectedArea = situation.selectedArea
  // 지도 응답에 마커가 없으면 center 가 (0,0)으로 내려온다. 그대로 쓰면 지도가
  // 아프리카 앞바다로 이동해 "지도가 안 나오는" 증상이 되므로 유효 좌표만 쓴다.
  const isValidCenter = (
    coordinate?: { latitude?: number; longitude?: number } | null,
  ): coordinate is { latitude: number; longitude: number } =>
    coordinate != null
    && Number.isFinite(coordinate.latitude)
    && Number.isFinite(coordinate.longitude)
    && (coordinate.latitude !== 0 || coordinate.longitude !== 0)

  const firstReportWithCoords = situation.reports.find(
    (report) => isValidCenter({ latitude: report.latitude, longitude: report.longitude }),
  )
  // 지역을 선택했으면 지역 중심(지도 응답 → 지역 좌표 → 첫 제보 좌표 순),
  // 아니면 현재 위치를 중심으로 쓴다.
  const areaCenter = selectedArea
    ? [
        situation.mapData?.center,
        { latitude: selectedArea.latitude, longitude: selectedArea.longitude },
        firstReportWithCoords
          ? { latitude: firstReportWithCoords.latitude, longitude: firstReportWithCoords.longitude }
          : null,
      ].find(isValidCenter) ?? null
    : null
  const mapCenter = selectedArea ? areaCenter : geolocation.location

  // 렌더마다 새 함수를 넘기면 지도가 마커를 전부 지웠다 다시 만든다.
  // 검색어를 한 글자 칠 때마다 그 일이 벌어진다.
  const openReportDetail = useCallback(
    (reportId: string) => {
      navigate(`/app/situations/reports/${reportId}${location.search}`)
    },
    [location.search, navigate],
  )

  return (
    <div className="situation-page">
      <SituationHeader />

      <div className="situation-toolbar">
        <LocationSearchBar
          keyword={situation.searchKeyword}
          suggestions={situation.searchSuggestions}
          isLoading={situation.isSearchLoading}
          error={situation.searchError}
          onKeywordChange={situation.setSearchKeyword}
          onSelect={situation.selectArea}
          onSubmit={situation.submitSearch}
          onClear={situation.clearSelectedArea}
        />
        <button
          className={situation.selectedTypes.length ? 'filter-icon-button active' : 'filter-icon-button'}
          type="button"
          aria-label="필터 열기"
          onClick={() => situation.setIsFilterOpen(true)}
        >
          <svg
            className="filter-funnel-icon"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth={1.8}
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
          >
            <path d="M4 5h16l-6.5 7.4V19l-3 1v-7.6L4 5Z" />
          </svg>
        </button>
      </div>

      {selectedArea ? <SelectedAreaHeader area={selectedArea} /> : null}

      {/*
        지역을 고르면 바로 위 SelectedAreaHeader 가 이미 지역명을 크게 보여준다.
        같은 이름을 '○○ 제보 지도'로 한 번 더 쓰면 긴 장소명일수록 화면만 밀리므로
        검색 전 화면에서만 제목을 둔다. 제목이 없는 동안에는 aria-label 이 이름을 준다.
      */}
      <section
        className="situation-content-section"
        aria-labelledby={selectedArea ? undefined : 'risk-map-section-title'}
        aria-label={selectedArea ? `${selectedArea.name} 제보 지도` : undefined}
      >
        {selectedArea ? null : (
          <h2 id="risk-map-section-title" className="situation-section-title">내 주변 지도</h2>
        )}
        <SituationMap
          center={mapCenter}
          reports={situation.reports}
          showCurrentLocation={!selectedArea}
          isLocating={geolocation.status === 'idle' || geolocation.status === 'checking'}
          // 지역을 검색해 지도가 그쪽으로 옮겨간 뒤에도 내 위치로 돌아올 수 있어야 하므로
          // center 와 별개로 측위 좌표를 그대로 넘긴다.
          currentLocation={geolocation.location}
          // 장소 선택은 마커가 지역 제한 없는 전체 피드다. fitToReports 를 켜면 서울
          // 전역 마커에 맞추려고 지도가 줌아웃돼 "그 장소를 중심으로 본다"가 깨진다.
          fitToReports={selectedArea?.type === 'area'}
          statusMessage={
            selectedArea
              ? undefined
              : geolocation.status === 'success'
                ? undefined
                : geolocation.message
          }
          onSelectReport={openReportDetail}
        />
        <MapLegendNotes />
      </section>

      {selectedArea ? (
        <AreaStatusSummary
          status={situation.areaStatus}
          isLoading={situation.isAreaStatusLoading}
          error={situation.areaStatusError}
          onRefresh={situation.refreshAreaStatus}
        />
      ) : null}

      <ReportFilterSheet
        open={situation.isFilterOpen}
        selectedTypes={situation.selectedTypes}
        onClose={() => situation.setIsFilterOpen(false)}
        onApply={situation.setSelectedTypes}
      />
    </div>
  )
}
