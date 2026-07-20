import { reportSortLabels, type ReportSort } from './types'

type ReportSortSelectProps = {
  value: ReportSort
  onChange: (sort: ReportSort) => void
}

const sortOptions: ReportSort[] = ['latest', 'risk_asc', 'risk_desc']

export function ReportSortSelect({ value, onChange }: ReportSortSelectProps) {
  return (
    <label className="report-sort-select">
      <span>정렬</span>
      <select
        value={value}
        aria-label="실시간 제보 정렬"
        onChange={(event) => onChange(event.target.value as ReportSort)}
      >
        {sortOptions.map((option) => (
          <option key={option} value={option}>
            {reportSortLabels[option]}
          </option>
        ))}
      </select>
    </label>
  )
}
