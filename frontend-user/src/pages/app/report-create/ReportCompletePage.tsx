import {
  DEFAULT_REPORT_CATEGORY,
  DEFAULT_REPORT_METHOD,
  REPORT_CATEGORY_LABELS,
  REPORT_METHOD_LABELS,
} from './reportConstants'
import type { ReportFormData } from './reportTypes'
import './report-create.css'

interface ReportCompletePageProps {
  formData?: ReportFormData
  onRestart?: () => void
}

const fallbackFormData: ReportFormData = {
  method: DEFAULT_REPORT_METHOD,
  category: DEFAULT_REPORT_CATEGORY,
  selectedTags: [],
  content: '',
  location: null,
}

function formatCoordinate(value: number): string {
  return value.toFixed(5)
}

export function ReportCompletePage({ formData = fallbackFormData, onRestart }: ReportCompletePageProps) {
  const locationText = formData.location
    ? `위도 ${formatCoordinate(formData.location.latitude)}, 경도 ${formatCoordinate(formData.location.longitude)}`
    : '위치 정보 없음'
  const categoryLabel = formData.category ? REPORT_CATEGORY_LABELS[formData.category] : '선택 없음'

  return (
    <section className="report-create-page" aria-labelledby="report-complete-title">
      <div className="report-complete-card">
        <span className="report-complete-mark" aria-hidden="true">
          ✓
        </span>
        <h1 id="report-complete-title">제보가 완료되었습니다.</h1>
        <p>안전한 지역 정보를 만드는 데 도움을 주셔서 감사합니다.</p>
        <dl className="report-summary-list">
          <div>
            <dt>제보 방식</dt>
            <dd>{REPORT_METHOD_LABELS[formData.method]}</dd>
          </div>
          <div>
            <dt>선택한 카테고리</dt>
            <dd>{categoryLabel}</dd>
          </div>
          <div>
            <dt>위치 정보</dt>
            <dd>{locationText}</dd>
          </div>
          <div>
            <dt>상황 태그</dt>
            <dd>{formData.selectedTags.length > 0 ? formData.selectedTags.join(', ') : '선택 없음'}</dd>
          </div>
          <div>
            <dt>제보 내용</dt>
            <dd>{formData.content}</dd>
          </div>
        </dl>
        <button type="button" className="report-primary-button" onClick={onRestart}>
          제보하기로 돌아가기
        </button>
      </div>
    </section>
  )
}
