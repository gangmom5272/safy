import { type ChangeEvent, useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { PageHeader } from '../../../components/common/PageHeader'
import { mockLocations } from '../../../mocks/locations'
import { resolveLocation } from '../home/homeApi'
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

export function ReportTypePage({ initialFormData, onStart }: ReportTypePageProps) {
  const navigate = useNavigate()
  const [method, setMethod] = useState<ReportMethod>(initialFormData?.method ?? DEFAULT_REPORT_METHOD)
  const [category, setCategory] = useState<ReportCategory | null>(initialFormData?.category ?? null)
  const [selectedTags, setSelectedTags] = useState<ReportTag[]>(
    initialFormData?.selectedTags ?? [],
  )
  const [content, setContent] = useState(initialFormData?.content ?? '')
  const [contentTouched, setContentTouched] = useState(false)
  const [resolvedLocation, setResolvedLocation] = useState<{ key: string; name: string } | null>(null)
  const { status, location, message, requestLocation } = useGeolocation()

  useEffect(() => {
    if (status !== 'success' || !location) {
      return
    }

    let isCurrent = true
    const locationKey = `${location.latitude}:${location.longitude}`
    const mockLocationName = getNearestMockLocationName(location.latitude, location.longitude)
    void resolveLocation(location.latitude, location.longitude)
      .then((resolvedLocation) => {
        if (isCurrent) setResolvedLocation({ key: locationKey, name: resolvedLocation.areaName || mockLocationName })
      })
      .catch(() => {
        if (isCurrent) setResolvedLocation({ key: locationKey, name: mockLocationName })
      })

    return () => {
      isCurrent = false
    }
  }, [location, status])

  const handleCategorySelect = (nextCategory: ReportCategory) => {
    setCategory(nextCategory)
    setSelectedTags([])
  }

  const handleTagToggle = (tag: ReportTag) => {
    setSelectedTags((currentTags) => currentTags.includes(tag) ? [] : [tag])
  }

  const handleContentChange = (event: ChangeEvent<HTMLTextAreaElement>) => {
    setContentTouched(true)
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

  const ctaLabel = method === 'live' ? '실시간 영상 제보 시작하기' : '사진·영상 제보 등록하기'
  const hasLocation = status === 'success' && location
  const locationKey = location ? `${location.latitude}:${location.longitude}` : ''
  const locationName = resolvedLocation?.key === locationKey
    ? resolvedLocation.name
    : '위치명을 확인하는 중입니다.'
  const availableTags = category ? REPORT_TAG_LABELS[category] : []
  const trimmedContentLength = content.trim().length
  const isContentValid = trimmedContentLength >= REPORT_CONTENT_MIN_LENGTH
  const contentErrorMessage =
    trimmedContentLength === 0
      ? '제보 내용을 입력해주세요.'
      : `제보 내용은 공백 제외 ${REPORT_CONTENT_MIN_LENGTH}자 이상 입력해주세요.`
  const canStart = Boolean(method && category && selectedTags.length === 1 && hasLocation && isContentValid)

  return (
    <section className="report-create-page report-type-page" aria-labelledby="report-create-title">
      <PageHeader title="제보하기" titleId="report-create-title" onBack={() => navigate(-1)} />

      <p className="report-type-intro">현장의 위험 상황을 실시간으로 공유해주세요.</p>

      <div className="report-safety-box" role="note">
        <SafetyIcon />
        <div>
          <strong>안전한 제보를 위해 꼭 확인해주세요!</strong>
          <p>허위 제보는 제재 대상이 될 수 있습니다. 안전한 장소에서 현장 상황만 촬영해주세요.</p>
        </div>
      </div>

      <section className="report-form-section" aria-labelledby="report-method-title">
        <h2 id="report-method-title" className="report-form-section-title">제보 방식 선택</h2>
        <div className="report-method-grid" role="radiogroup" aria-label="제보 방식">
          <button
            type="button"
            className={`report-method-button ${method === 'live' ? 'selected' : ''}`}
            role="radio"
            aria-checked={method === 'live'}
            onClick={() => setMethod('live')}
          >
            <span className="report-radio-dot" aria-hidden="true" />
            <MethodIcon method="live" />
            <span className="report-method-copy">
              <span className="report-method-title">실시간 영상 제보</span>
            </span>
          </button>

          <button
            type="button"
            className={`report-method-button ${method === 'photo' ? 'selected' : ''}`}
            role="radio"
            aria-checked={method === 'photo'}
            onClick={() => setMethod('photo')}
          >
            <span className="report-radio-dot" aria-hidden="true" />
            <MethodIcon method="photo" />
            <span className="report-method-copy">
              <span className="report-method-title">사진·영상 제보</span>
            </span>
          </button>
        </div>
      </section>

      <section className="report-form-section" aria-labelledby="report-location-title">
        <h2 id="report-location-title" className="report-form-section-title">현재 위치</h2>
        <div className="report-location-card">
          <div className="report-map-placeholder" aria-live="polite">
          <div className="report-mini-map" aria-hidden="true">
            <span className="report-map-park" />
            <span className="report-map-building building-one" />
            <span className="report-map-building building-two" />
            {hasLocation ? <span className="report-location-marker" /> : null}
          </div>
          <div className="report-location-status">
            <strong className={hasLocation ? 'location-confirmed' : ''}>
              {hasLocation ? <LocationIcon /> : null}
              {message}
            </strong>
            {status === 'idle' ? <p>위치 확인 전입니다.</p> : null}
            {status === 'checking' ? <p>브라우저 위치 권한을 확인하고 있습니다.</p> : null}
            {hasLocation ? (
              <p className="report-location-name">{locationName}</p>
            ) : null}
            {hasLocation ? <p className="report-location-guide">제보 위치는 현재 위치로 자동 설정됩니다.</p> : null}
            {status !== 'checking' ? (
              <button type="button" className="report-secondary-button" onClick={requestLocation}>
                위치 다시 확인
              </button>
            ) : null}
          </div>
          </div>
        </div>
      </section>

      <section className="report-form-section" aria-labelledby="report-classification-title">
        <h2 id="report-classification-title" className="report-form-section-title">제보 분류</h2>
        <div className="report-classification-card">
          <div className="report-classification-group">
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
                <CategoryIcon category={item} />
                <span>{REPORT_CATEGORY_LABELS[item]}</span>
              </button>
            ))}
          </div>
        </div>

          {category ? (
            <div className="report-classification-group report-classification-group-separated">
            <h3 className="report-section-label">세부 상황</h3>
            <p>현장 상황에 가장 가까운 항목 하나를 선택해주세요.</p>
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
        </div>
      </section>

      <section className="report-form-section" aria-labelledby="report-content-title">
        <h2 id="report-content-title" className="report-form-section-title">제보 내용</h2>
        <div className="report-content-card">
          <textarea
          className="report-content-textarea"
          value={content}
          maxLength={REPORT_CONTENT_MAX_LENGTH}
          rows={5}
          aria-describedby="report-content-guide report-content-count"
          aria-invalid={contentTouched && !isContentValid}
          placeholder="현장 상황을 구체적으로 작성해주세요."
          onChange={handleContentChange}
        />
          <div className="report-content-meta">
            <p id="report-content-guide" className={contentTouched && !isContentValid ? 'report-error-text' : 'report-create-muted'}>
              {!isContentValid ? contentErrorMessage : '제보 내용이 입력되었습니다.'}
            </p>
            <span id="report-content-count" className="report-content-count">
              {content.length} / {REPORT_CONTENT_MAX_LENGTH}
            </span>
          </div>
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

function MethodIcon({ method }: { method: ReportMethod }) {
  return method === 'live' ? (
    <svg className="report-method-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M15 10l4.5-2.5v9L15 14" />
      <rect x="3" y="6" width="12" height="12" rx="2" />
    </svg>
  ) : (
    <svg className="report-method-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <rect x="3" y="4" width="18" height="16" rx="2" />
      <circle cx="8.5" cy="9" r="1.5" />
      <path d="M21 15l-5-5L5 20" />
    </svg>
  )
}

function LocationIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M20 10c0 5-8 11-8 11S4 15 4 10a8 8 0 1 1 16 0Z" />
      <circle cx="12" cy="10" r="2.5" />
    </svg>
  )
}

function SafetyIcon() {
  return (
    <svg className="report-safety-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M12 3 20 6v5c0 5-3.4 8.4-8 10-4.6-1.6-8-5-8-10V6l8-3Z" />
      <path d="m8.5 12 2.2 2.2 4.8-5" />
    </svg>
  )
}

function CategoryIcon({ category }: { category: ReportCategory }) {
  const paths: Record<ReportCategory, React.ReactNode> = {
    crowd: <><circle cx="9" cy="8" r="3" /><circle cx="17" cy="9" r="2.5" /><path d="M3 19c0-3.2 2.7-5.5 6-5.5s6 2.3 6 5.5M14 14.5c3.5-.8 7 1.2 7 4.5" /></>,
    accident: <><path d="M12 3 2.8 20h18.4L12 3Z" /><path d="M12 9v4M12 17h.01" /></>,
    weather: <><path d="M7 16a4 4 0 1 1 1.7-7.6A5.5 5.5 0 0 1 19 11a3 3 0 0 1-1 5H7Z" /><path d="m8 19-1 2M13 19l-1 2M18 19l-1 2" /></>,
    facility: <><path d="M4 21v-8M20 21v-8M3 13h18L18 6H6l-3 7Z" /><path d="m8 7 3 6M15 7l3 6" /></>,
    etc: <><circle cx="5" cy="12" r="1" fill="currentColor" stroke="none" /><circle cx="12" cy="12" r="1" fill="currentColor" stroke="none" /><circle cx="19" cy="12" r="1" fill="currentColor" stroke="none" /></>,
  }

  return <svg className="report-category-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[category]}</svg>
}

function getNearestMockLocationName(latitude: number, longitude: number) {
  const nearestLocation = mockLocations.reduce((nearest, candidate) => {
    const nearestDistance = (nearest.latitude - latitude) ** 2 + (nearest.longitude - longitude) ** 2
    const candidateDistance = (candidate.latitude - latitude) ** 2 + (candidate.longitude - longitude) ** 2
    return candidateDistance < nearestDistance ? candidate : nearest
  })
  const distance = (nearestLocation.latitude - latitude) ** 2 + (nearestLocation.longitude - longitude) ** 2

  return distance <= 0.0025 ? nearestLocation.areaName : '위치 정보를 확인할 수 없습니다.'
}
