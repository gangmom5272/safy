import { formatDateTime } from '../../../utils/dateTime'
import type { LocationSearchResult } from './types'

type SelectedAreaHeaderProps = {
  area: LocationSearchResult
}
export function SelectedAreaHeader({ area }: SelectedAreaHeaderProps) {
  return (
    <section className="selected-area-header" aria-label="선택된 지역">
      <div>
        <h2>{area.name} 주변</h2>
        <span className="selected-area-address">{area.address}</span>
        <span className="selected-area-time">{formatDateTime(new Date())} 기준</span>
      </div>
    </section>
  )
}
