import type { RefObject } from 'react'
import { useNavigate } from 'react-router'
import { EmptyState, ErrorState, LoadingSkeleton } from './SituationStates'
import { LiveReportCard } from './LiveReportCard'
import {
  reportSortLabels,
  reportTypeLabels,
  type LiveReport,
  type ReportSort,
  type ReportType,
} from './types'

type LiveReportSectionProps = {
  reports: LiveReport[]
  sort: ReportSort
  selectedTypes: ReportType[]
  isLoading: boolean
  error: unknown
  listRef: RefObject<HTMLDivElement | null>
  onRetry: () => void
}

export function LiveReportSection({
  reports,
  sort,
  selectedTypes,
  isLoading,
  error,
  listRef,
  onRetry,
}: LiveReportSectionProps) {
  const navigate = useNavigate()

  return (
    <section className="live-report-section" aria-labelledby="live-report-title">
      <div className="section-title-row">
        <div>
          <h2 id="live-report-title">실시간 제보</h2>
          <p>{reportSortLabels[sort]}</p>
        </div>
        <button type="button" onClick={() => navigate('/app/situations')}>
          지도 보기
        </button>
      </div>

      {selectedTypes.length ? (
        <div className="selected-filter-chips" aria-label="선택된 필터">
          {selectedTypes.map((type) => (
            <span key={type}>{reportTypeLabels[type]}</span>
          ))}
        </div>
      ) : null}

      <div ref={listRef} className="report-list-scroll">
        {isLoading ? <LoadingSkeleton /> : null}
        {!isLoading && error ? (
          <ErrorState
            message="실시간 제보 목록을 불러오지 못했습니다."
            actionLabel="다시 시도"
            onAction={onRetry}
          />
        ) : null}
        {!isLoading && !error && reports.length === 0 ? (
          <EmptyState message="조건에 맞는 실시간 제보가 없습니다." />
        ) : null}
        {!isLoading && !error && reports.length > 0 ? (
          <div className="report-card-list">
            {reports.map((report) => (
              <LiveReportCard key={report.id} report={report} />
            ))}
          </div>
        ) : null}
      </div>
    </section>
  )
}
