import { type ChangeEvent, useState } from 'react'
import {
  DEFAULT_REPORT_METHOD,
  REPORT_CONTENT_MAX_LENGTH,
  REPORT_CONTENT_MIN_LENGTH,
  REPORT_CATEGORIES,
  REPORT_CATEGORY_LABELS,
  REPORT_TAG_LABELS,
} from './reportConstants'
import type { ReportCategory, ReportFormData, ReportMethod, ReportTag } from './reportTypes'
import { useGeolocation } from './useGeolocation'
import './report-create.css'

interface ReportTypePageProps {
  initialFormData?: ReportFormData
  onStart?: (formData: ReportFormData) => void
}

function formatCoordinate(value: number): string {
  return value.toFixed(5)
}

export function ReportTypePage({ initialFormData, onStart }: ReportTypePageProps) {
  const [method, setMethod] = useState<ReportMethod>(initialFormData?.method ?? DEFAULT_REPORT_METHOD)
  const [category, setCategory] = useState<ReportCategory | null>(initialFormData?.category ?? null)
  const [selectedTags, setSelectedTags] = useState<ReportTag[]>(
    initialFormData?.selectedTags ?? [],
  )
  const [content, setContent] = useState(initialFormData?.content ?? '')
  const { status, location, message, requestLocation } = useGeolocation()

  const handleCategorySelect = (nextCategory: ReportCategory) => {
    setCategory(nextCategory)
    setSelectedTags([])
  }

  const handleTagToggle = (tag: ReportTag) => {
    setSelectedTags((currentTags) =>
      currentTags.includes(tag)
        ? currentTags.filter((currentTag) => currentTag !== tag)
        : [...currentTags, tag],
    )
  }

  const handleContentChange = (event: ChangeEvent<HTMLTextAreaElement>) => {
    setContent(event.target.value.slice(0, REPORT_CONTENT_MAX_LENGTH))
  }

  const handleStart = () => {
    if (!canStart || !category) {
      return
    }

    onStart?.({
      method,
      category,
      selectedTags,
      content: content.trim(),
      location,
    })
  }

  const ctaLabel = method === 'live' ? '실시간 영상 제보 시작하기' : '사진 제보 시작하기'
  const hasLocation = status === 'success' && location
  const availableTags = category ? REPORT_TAG_LABELS[category] : []
  const trimmedContentLength = content.trim().length
  const isContentValid = trimmedContentLength >= REPORT_CONTENT_MIN_LENGTH
  const contentErrorMessage =
    trimmedContentLength === 0
      ? '제보 내용을 입력해주세요.'
      : `제보 내용은 공백 제외 ${REPORT_CONTENT_MIN_LENGTH}자 이상 입력해주세요.`
  const canStart = Boolean(method && category && hasLocation && isContentValid)

  return (
    <section className="report-create-page" aria-labelledby="report-create-title">
      <header className="report-create-header">
        <h1 id="report-create-title">제보하기</h1>
        <p>현장의 위험 상황을 실시간으로 공유해주세요.</p>
      </header>

      <div className="report-safety-box" role="note">
        <strong>안전한 제보를 위해 꼭 확인해주세요!</strong>
        <p>허위 제보는 제재 대상이 될 수 있습니다. 안전한 장소에서 현장 상황만 촬영해주세요.</p>
      </div>

      <section className="report-create-card" aria-labelledby="report-method-title">
        <h2 id="report-method-title">제보 방식 선택</h2>
        <div className="report-method-grid" role="radiogroup" aria-label="제보 방식">
          <button
            type="button"
            className={`report-method-button ${method === 'live' ? 'selected' : ''}`}
            role="radio"
            aria-checked={method === 'live'}
            onClick={() => setMethod('live')}
          >
            <span className="report-radio-dot" aria-hidden="true" />
            <span>
              <span className="report-method-title">실시간 영상 제보</span>
              <span className="report-create-muted">WebRTC로 실시간 상황을 공유해요.</span>
            </span>
            <span className="report-recommend-badge">추천</span>
          </button>

          <button
            type="button"
            className={`report-method-button ${method === 'photo' ? 'selected' : ''}`}
            role="radio"
            aria-checked={method === 'photo'}
            onClick={() => setMethod('photo')}
          >
            <span className="report-radio-dot" aria-hidden="true" />
            <span>
              <span className="report-method-title">사진 제보</span>
              <span className="report-create-muted">현장 사진을 찍어 공유해요.</span>
            </span>
          </button>
        </div>
      </section>

      <section className="report-create-card" aria-labelledby="report-location-title">
        <h2 id="report-location-title">현재 위치</h2>
        <div className="report-map-placeholder" aria-live="polite">
          {hasLocation ? <span className="report-location-marker" aria-hidden="true" /> : null}
          <div className="report-location-status">
            <strong>{message}</strong>
            {status === 'idle' ? <p>위치 확인 전입니다.</p> : null}
            {status === 'checking' ? <p>브라우저 위치 권한을 확인하고 있습니다.</p> : null}
            {hasLocation ? (
              <p>
                위도 {formatCoordinate(location.latitude)}, 경도 {formatCoordinate(location.longitude)}
              </p>
            ) : null}
            {status !== 'idle' && status !== 'checking' && status !== 'success' ? (
              <>
                <p className="report-error-text">{message}</p>
                <button type="button" className="report-secondary-button" onClick={requestLocation}>
                  위치 다시 확인
                </button>
              </>
            ) : null}
          </div>
        </div>
      </section>

      <section className="report-create-card" aria-labelledby="report-classification-title">
        <h2 id="report-classification-title">제보 분류</h2>
        <div className="report-classification-group">
          <h3 className="report-section-label">제보 카테고리</h3>
          <div className="report-category-grid" role="radiogroup" aria-label="제보 카테고리">
            {REPORT_CATEGORIES.map((item) => (
              <button
                key={item}
                type="button"
                className={`report-category-button ${category === item ? 'selected' : ''}`}
                role="radio"
                aria-checked={category === item}
                onClick={() => handleCategorySelect(item)}
              >
                {REPORT_CATEGORY_LABELS[item]}
              </button>
            ))}
          </div>
        </div>

        {category ? (
          <div className="report-classification-group report-classification-group-separated">
            <h3 className="report-section-label">세부 상황</h3>
            <p>현장 상황에 맞는 항목을 여러 개 선택해주세요.</p>
            <div className="report-tag-grid" aria-label="세부 상황">
              {availableTags.map((tag) => {
                const isSelected = selectedTags.includes(tag)

                return (
                  <button
                    key={tag}
                    type="button"
                    className={`report-tag-button ${isSelected ? 'selected' : ''}`}
                    aria-pressed={isSelected}
                    onClick={() => handleTagToggle(tag)}
                  >
                    {tag}
                  </button>
                )
              })}
            </div>
          </div>
        ) : null}
      </section>

      <section className="report-create-card" aria-labelledby="report-content-title">
        <h2 id="report-content-title">제보 내용</h2>
        <p>현재 현장 상황을 구체적으로 작성해주세요.</p>
        <textarea
          className="report-content-textarea"
          value={content}
          maxLength={REPORT_CONTENT_MAX_LENGTH}
          rows={5}
          aria-describedby="report-content-guide report-content-count"
          aria-invalid={!isContentValid}
          placeholder="예: 2번 출구 앞에 사람이 몰려 이동하기 어렵고, 인파가 계속 증가하고 있습니다."
          onChange={handleContentChange}
        />
        <div className="report-content-meta">
          <p id="report-content-guide" className={!isContentValid ? 'report-error-text' : 'report-create-muted'}>
            {!isContentValid ? contentErrorMessage : '제보 내용이 입력되었습니다.'}
          </p>
          <span id="report-content-count" className="report-content-count">
            {content.length} / {REPORT_CONTENT_MAX_LENGTH}
          </span>
        </div>
      </section>

      <div className="report-create-actions">
        <button type="button" className="report-primary-button" disabled={!canStart} onClick={handleStart}>
          {ctaLabel}
        </button>
      </div>
    </section>
  )
}
