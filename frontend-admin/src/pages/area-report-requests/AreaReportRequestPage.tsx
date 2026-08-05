import { useEffect, useState, type FormEvent } from 'react'
import { AdminSection } from '../../components/admin/AdminSection'
import {
  createAreaReportRequest,
  getRequestPlaces,
  type AreaReportRequestResult,
  type RequestPlace,
} from './areaReportRequestApi'
import './areaReportRequest.css'

export function AreaReportRequestPage() {
  const [places, setPlaces] = useState<RequestPlace[]>([])
  const [placeId, setPlaceId] = useState('')
  const [title, setTitle] = useState('현장 상황을 제보해 주세요')
  const [content, setContent] = useState('현재 현장 상황을 사진 또는 영상과 함께 제보해 주세요.')
  const [expiresInMinutes, setExpiresInMinutes] = useState(30)
  const [result, setResult] = useState<AreaReportRequestResult | null>(null)
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    void getRequestPlaces()
      .then(setPlaces)
      .catch(() => setError('요청 가능한 구역을 불러오지 못했습니다.'))
  }, [])

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!placeId || submitting) return
    setSubmitting(true)
    setError('')
    setResult(null)
    try {
      setResult(await createAreaReportRequest({
        placeId: Number(placeId),
        title: title.trim(),
        content: content.trim(),
        expiresInMinutes,
      }))
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '제보 요청을 보내지 못했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="page-stack area-request-page">
      <AdminSection title="현장 제보 요청">
        <p className="area-request-page__description">
          최근 10분 안에 이 구역에서 위치가 확인되고, 최근 5분 안에 접속한 동의 사용자에게 알림을 보냅니다.
        </p>
        <form className="area-request-form" onSubmit={(event) => void submit(event)}>
          <label>
            요청 구역
            <select value={placeId} onChange={(event) => setPlaceId(event.target.value)} required>
              <option value="">구역을 선택하세요</option>
              {places.map((place) => <option key={place.id} value={place.id}>{place.name}</option>)}
            </select>
          </label>
          <label>
            알림 제목
            <input value={title} maxLength={120} onChange={(event) => setTitle(event.target.value)} required />
          </label>
          <label>
            요청 내용
            <textarea value={content} maxLength={500} rows={5} onChange={(event) => setContent(event.target.value)} required />
          </label>
          <label>
            요청 유효 시간
            <select value={expiresInMinutes} onChange={(event) => setExpiresInMinutes(Number(event.target.value))}>
              <option value={15}>15분</option>
              <option value={30}>30분</option>
              <option value={60}>60분</option>
            </select>
          </label>
          {error && <p className="area-request-message area-request-message--error" role="alert">{error}</p>}
          {result && (
            <p className="area-request-message area-request-message--success" role="status">
              {result.placeName}의 조건 충족 사용자 {result.recipientCount}명에게 요청했습니다.
            </p>
          )}
          <button className="primary-button" type="submit" disabled={!placeId || submitting}>
            {submitting ? '전송 중...' : '제보 요청 보내기'}
          </button>
        </form>
      </AdminSection>
    </section>
  )
}
