import { useEffect, useRef, useState } from 'react'
import { reportTypeLabels, reportTypes, type ReportType } from './types'

type ReportFilterSheetProps = {
  open: boolean
  selectedTypes: ReportType[]
  onClose: () => void
  onApply: (types: ReportType[]) => void
}

export function ReportFilterSheet({
  open,
  selectedTypes,
  onClose,
  onApply,
}: ReportFilterSheetProps) {
  if (!open) {
    return null
  }

  return (
    <ReportFilterSheetContent
      selectedTypes={selectedTypes}
      onClose={onClose}
      onApply={onApply}
    />
  )
}

type ReportFilterSheetContentProps = Omit<ReportFilterSheetProps, 'open'>

function ReportFilterSheetContent({
  selectedTypes,
  onClose,
  onApply,
}: ReportFilterSheetContentProps) {
  const [draftTypes, setDraftTypes] = useState<ReportType[]>(() => selectedTypes)
  const sheetRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    window.setTimeout(() => sheetRef.current?.focus(), 0)

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose()
      }
    }

    document.addEventListener('keydown', handleKeyDown)

    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [onClose])

  const toggleType = (type: ReportType) => {
    setDraftTypes((currentTypes) =>
      currentTypes.includes(type)
        ? currentTypes.filter((currentType) => currentType !== type)
        : [...currentTypes, type],
    )
  }

  return (
    <div className="filter-sheet-backdrop" role="presentation" onMouseDown={onClose}>
      <div
        ref={sheetRef}
        className="filter-sheet"
        role="dialog"
        aria-modal="true"
        aria-labelledby="filter-sheet-title"
        tabIndex={-1}
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className="filter-sheet-header">
          <div>
            <p>실시간 제보 유형</p>
            <h2 id="filter-sheet-title">필터</h2>
          </div>
          <button type="button" aria-label="필터 닫기" onClick={onClose}>
            닫기
          </button>
        </div>

        <div className="filter-options" aria-label="제보 유형 필터">
          {reportTypes.map((type) => (
            <label key={type} className={draftTypes.includes(type) ? 'selected' : undefined}>
              <input
                type="checkbox"
                checked={draftTypes.includes(type)}
                onChange={() => toggleType(type)}
              />
              <span>{reportTypeLabels[type]}</span>
            </label>
          ))}
        </div>

        <div className="filter-sheet-actions">
          <button className="secondary" type="button" onClick={() => setDraftTypes([])}>
            전체 해제
          </button>
          <button
            className="primary"
            type="button"
            onClick={() => {
              onApply(draftTypes)
              onClose()
            }}
          >
            적용
          </button>
        </div>
      </div>
    </div>
  )
}
