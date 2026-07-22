import { useEffect, useRef, useState } from 'react'
import { reportSortLabels, type ReportSort } from './types'

type ReportSortSelectProps = {
  value: ReportSort
  onChange: (sort: ReportSort) => void
}

const sortOptions: ReportSort[] = ['latest', 'risk_desc', 'risk_asc']

export function ReportSortSelect({ value, onChange }: ReportSortSelectProps) {
  const [isOpen, setIsOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement | null>(null)
  const label = reportSortLabels[value]

  useEffect(() => {
    if (!isOpen) {
      return
    }

    const closeOnOutsideClick = (event: MouseEvent) => {
      if (!containerRef.current?.contains(event.target as Node)) {
        setIsOpen(false)
      }
    }

    document.addEventListener('mousedown', closeOnOutsideClick)

    return () => document.removeEventListener('mousedown', closeOnOutsideClick)
  }, [isOpen])

  const selectSort = (sort: ReportSort) => {
    onChange(sort)
    setIsOpen(false)
  }

  return (
    <div className="report-sort-select" ref={containerRef}>
      <button
        className="sort-icon-button"
        type="button"
        aria-label={`정렬 기준 선택: ${label}`}
        title={label}
        aria-haspopup="menu"
        aria-expanded={isOpen}
        onClick={() => setIsOpen((open) => !open)}
      >
        <SortIcon value={value} />
      </button>

      {isOpen ? (
        <div className="sort-dropdown" role="menu" aria-label="정렬 기준">
          {sortOptions.map((option) => (
            <button
              key={option}
              className={option === value ? 'selected' : undefined}
              type="button"
              role="menuitemradio"
              aria-checked={option === value}
              onClick={() => selectSort(option)}
            >
              <span>{reportSortLabels[option]}</span>
              {option === value ? <span aria-hidden="true">✓</span> : null}
            </button>
          ))}
        </div>
      ) : null}
    </div>
  )
}

function SortIcon({ value }: { value: ReportSort }) {
  if (value === 'latest') {
    return (
      <svg
        className="sort-arrow-icon"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth={2}
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden="true"
      >
        <path d="M8 18V6M4.5 9.5 8 6l3.5 3.5" />
        <path d="M16 6v12M12.5 14.5 16 18l3.5-3.5" />
      </svg>
    )
  }

  return (
    <svg
      className="sort-arrow-icon"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {value === 'risk_desc' ? <path d="M12 19V5M7 10l5-5 5 5" /> : null}
      {value === 'risk_asc' ? <path d="M12 5v14M7 14l5 5 5-5" /> : null}
    </svg>
  )
}
