import { useState } from 'react'
import {
  DEFAULT_REPORT_CATEGORY,
  DEFAULT_REPORT_METHOD,
  REPORT_CATEGORIES,
  REPORT_CATEGORY_LABELS,
} from './reportConstants'
import type { ReportCategory, ReportFormData, ReportMethod } from './reportTypes'
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
  const [category, setCategory] = useState<ReportCategory>(
    initialFormData?.category ?? DEFAULT_REPORT_CATEGORY,
  )
  const { status, location, message, requestLocation } = useGeolocation()

  const handleStart = () => {
    onStart?.({
      method,
      category,
      location,
    })
  }

  const ctaLabel = method === 'live' ? '실시간 영상 제보 시작하기' : '사진 제보 시작하기'
  const hasLocation = status === 'success' && location

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

      <section className="report-create-card" aria-labelledby="report-category-title">
        <h2 id="report-category-title">제보 카테고리</h2>
        <div className="report-category-grid" role="radiogroup" aria-label="제보 카테고리">
          {REPORT_CATEGORIES.map((item) => (
            <button
              key={item}
              type="button"
              className={`report-category-button ${category === item ? 'selected' : ''}`}
              role="radio"
              aria-checked={category === item}
              onClick={() => setCategory(item)}
            >
              {REPORT_CATEGORY_LABELS[item]}
            </button>
          ))}
        </div>
      </section>

      <div className="report-create-actions">
        <button type="button" className="report-primary-button" onClick={handleStart}>
          {ctaLabel}
        </button>
      </div>
    </section>
  )
}
