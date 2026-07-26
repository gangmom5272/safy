import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { mockLocations } from '../../../mocks/locations'
import { formatDateTime } from '../../../utils/dateTime'
import { resolveLocation } from '../home/homeApi'
import {
  DEFAULT_REPORT_CATEGORY,
  DEFAULT_REPORT_METHOD,
  REPORT_CATEGORY_LABELS,
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

export function ReportCompletePage({ formData = fallbackFormData, onRestart }: ReportCompletePageProps) {
  const navigate = useNavigate()
  const [receivedAt] = useState(() => new Date())
  const [locationName, setLocationName] = useState(formData.location ? '위치명을 확인하는 중입니다.' : '위치 정보 없음')
  const categoryLabel = formData.category ? REPORT_CATEGORY_LABELS[formData.category] : '선택 없음'
  const methodLabel = formData.method === 'live' ? '실시간 영상 제보' : '사진·영상 제보'

  useEffect(() => {
    if (!formData.location) return

    let isCurrent = true
    const { latitude, longitude } = formData.location
    const fallbackLocationName = getNearestMockLocationName(latitude, longitude)
    void resolveLocation(latitude, longitude)
      .then((resolvedLocation) => {
        if (isCurrent) setLocationName(resolvedLocation.areaName || fallbackLocationName)
      })
      .catch(() => {
        if (isCurrent) setLocationName(fallbackLocationName)
      })

    return () => {
      isCurrent = false
    }
  }, [formData.location])

  const moveTo = (path: string) => {
    onRestart?.()
    navigate(path)
  }

  return (
    <section className="report-create-page report-complete-page" aria-labelledby="report-complete-title">
      <div className="report-complete-card">
        <span className="report-complete-mark" aria-hidden="true">
          <CheckIcon />
        </span>
        <div className="report-complete-message">
          <h1 id="report-complete-title">제보가 성공적으로 접수되었습니다.</h1>
          <p>신속한 제보 감사합니다.</p>
          <p>관리자가 내용을 확인한 후<br />처리 결과를 알려드립니다.</p>
        </div>
        <dl className="report-summary-list">
          <div>
            <dt><SummaryIcon name="method" />제보 방식</dt>
            <dd>{methodLabel}</dd>
          </div>
          <div>
            <dt><SummaryIcon name="category" />제보 유형</dt>
            <dd>{categoryLabel}</dd>
          </div>
          <div>
            <dt><SummaryIcon name="location" />위치</dt>
            <dd>{locationName}</dd>
          </div>
          <div>
            <dt><SummaryIcon name="time" />접수 시간</dt>
            <dd><time dateTime={receivedAt.toISOString()}>{formatDateTime(receivedAt)}</time></dd>
          </div>
        </dl>
        <div className="report-complete-actions">
          <button type="button" className="report-primary-button" onClick={() => moveTo('/app/situations')}>상황 확인으로 이동</button>
          <button type="button" className="report-complete-home-button" onClick={() => moveTo('/app')}>홈으로 돌아가기</button>
        </div>
      </div>
    </section>
  )
}

function CheckIcon() {
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round"><path d="m6.5 12.5 3.5 3.5 7.5-8" /></svg>
}

function SummaryIcon({ name }: { name: 'method' | 'category' | 'location' | 'time' }) {
  const paths = {
    method: <><rect x="3" y="6" width="13" height="12" rx="2" /><path d="m16 10 5-3v10l-5-3" /></>,
    category: <><path d="M4 5h7l9 9-6 6-9-9V5Z" /><circle cx="8.5" cy="8.5" r="1" /></>,
    location: <><path d="M20 10c0 5-8 11-8 11S4 15 4 10a8 8 0 1 1 16 0Z" /><circle cx="12" cy="10" r="2.5" /></>,
    time: <><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" /></>,
  }
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name]}</svg>
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
