import { AreaMap } from './AreaMap'
import { AreaStatusSummary } from './AreaStatusSummary'
import { LiveReportSection } from './LiveReportSection'
import { LocationSearchBar } from './LocationSearchBar'
import { ReportFilterSheet } from './ReportFilterSheet'
import { ReportSortSelect } from './ReportSortSelect'
import { SelectedAreaHeader } from './SelectedAreaHeader'
import { SituationHeader } from './SituationHeader'
import { useSituationCheck } from './useSituationCheck'
import './SituationsPage.css'

export function SituationsPage() {
  const situation = useSituationCheck()

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
        <ReportSortSelect value={situation.sort} onChange={situation.setSort} />
      </div>

      {situation.selectedArea ? (
        <>
          <SelectedAreaHeader area={situation.selectedArea} />
          <section className="situation-content-section" aria-labelledby="risk-map-section-title">
            <h2 id="risk-map-section-title" className="situation-section-title">위험 지역 지도</h2>
            <AreaMap
              mapData={situation.mapData}
              isLoading={situation.isMapLoading}
              error={situation.mapError}
              locationName={situation.selectedArea.name}
            />
          </section>
          <AreaStatusSummary
            status={situation.areaStatus}
            isLoading={situation.isAreaStatusLoading}
            error={situation.areaStatusError}
            onRefresh={situation.refreshAreaStatus}
          />
        </>
      ) : null}

      <LiveReportSection
        reports={situation.reports}
        sort={situation.sort}
        selectedTypes={situation.selectedTypes}
        isLoading={situation.isReportsLoading}
        error={situation.reportsError}
        listRef={situation.reportListRef}
        onRetry={() => void situation.retryReports()}
        emptyMessage="현재 등록된 실시간 제보가 없습니다."
      />

      <ReportFilterSheet
        open={situation.isFilterOpen}
        selectedTypes={situation.selectedTypes}
        onClose={() => situation.setIsFilterOpen(false)}
        onApply={situation.setSelectedTypes}
      />
    </div>
  )
}
