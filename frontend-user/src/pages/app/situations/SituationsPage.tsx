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
          className="filter-icon-button"
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
          <SelectedAreaHeader area={situation.selectedArea} onClear={situation.clearSelectedArea} />
          <AreaMap
            mapData={situation.mapData}
            isLoading={situation.isMapLoading}
            error={situation.mapError}
          >
            <AreaStatusSummary
              status={situation.areaStatus}
              isLoading={situation.isAreaStatusLoading}
              error={situation.areaStatusError}
              onRefresh={situation.refreshAreaStatus}
            />
          </AreaMap>
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
        emptyMessage={situation.submittedQuery ? '검색 결과가 없습니다.' : undefined}
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
