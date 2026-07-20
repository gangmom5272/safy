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

      <LocationSearchBar
        keyword={situation.searchKeyword}
        suggestions={situation.searchSuggestions}
        isLoading={situation.isSearchLoading}
        error={situation.searchError}
        onKeywordChange={situation.setSearchKeyword}
        onSelect={situation.selectArea}
        onFilterOpen={() => situation.setIsFilterOpen(true)}
      />

      {situation.selectedArea ? (
        <>
          <SelectedAreaHeader area={situation.selectedArea} onClear={situation.clearSelectedArea} />
          <AreaMap
            mapData={situation.mapData}
            isLoading={situation.isMapLoading}
            error={situation.mapError}
          />
          <AreaStatusSummary
            status={situation.areaStatus}
            isLoading={situation.isAreaStatusLoading}
            error={situation.areaStatusError}
            onRefresh={situation.refreshAreaStatus}
          />
        </>
      ) : null}

      <div className="report-toolbar">
        <button
          className="report-filter-button"
          type="button"
          onClick={() => situation.setIsFilterOpen(true)}
        >
          필터 {situation.selectedTypes.length ? `${situation.selectedTypes.length}` : ''}
        </button>
        <ReportSortSelect value={situation.sort} onChange={situation.setSort} />
      </div>

      <LiveReportSection
        reports={situation.reports}
        sort={situation.sort}
        selectedTypes={situation.selectedTypes}
        isLoading={situation.isReportsLoading}
        error={situation.reportsError}
        listRef={situation.reportListRef}
        onRetry={() => void situation.retryReports()}
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
