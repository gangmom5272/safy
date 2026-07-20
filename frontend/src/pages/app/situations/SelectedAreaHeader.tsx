import type { LocationSearchResult } from './types'

type SelectedAreaHeaderProps = {
  area: LocationSearchResult
  onClear: () => void
}

export function SelectedAreaHeader({ area, onClear }: SelectedAreaHeaderProps) {
  return (
    <section className="selected-area-header" aria-label="선택된 지역">
      <div>
        <p>선택 지역</p>
        <h2>{area.name}</h2>
        <span>{area.address}</span>
      </div>
      <button type="button" onClick={onClear}>
        지역 변경
      </button>
    </section>
  )
}
