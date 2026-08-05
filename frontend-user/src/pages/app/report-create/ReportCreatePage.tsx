import { useCallback, useEffect, useState } from 'react'
import { useSearchParams } from 'react-router'
import { DEFAULT_REPORT_METHOD } from './reportConstants'
import { LiveReportPage } from './LiveReportPage'
import { MediaReportPage } from './MediaReportPage'
import { ReportCompletePage } from './ReportCompletePage'
import { ReportTypePage } from './ReportTypePage'
import type { ReportFormData, ReportStep } from './reportTypes'
import { getAreaReportRequest, type AreaReportRequest } from './areaReportRequestApi'
import './report-create.css'

function createInitialFormData(): ReportFormData {
  return {
    method: DEFAULT_REPORT_METHOD,
    category: null,
    selectedTags: [],
    content: '',
    location: null,
  }
}

export function ReportCreatePage() {
  const [searchParams] = useSearchParams()
  const requestId = searchParams.get('requestId')
  const [areaRequest, setAreaRequest] = useState<AreaReportRequest | null>(null)
  const [step, setStep] = useState<ReportStep>('select')
  const [formData, setFormData] = useState<ReportFormData>(() => createInitialFormData())

  useEffect(() => {
    if (!requestId) return
    let current = true
    void getAreaReportRequest(requestId)
      .then((request) => { if (current) setAreaRequest(request) })
      .catch(() => undefined)
    return () => { current = false }
  }, [requestId])

  const requestBanner = areaRequest && (
    <aside className="area-report-request-banner">
      <strong>{areaRequest.title}</strong>
      <p>{areaRequest.content}</p>
      <small>{areaRequest.placeName} · {new Date(areaRequest.expiresAt) < new Date() ? '요청 시간 종료' : '요청 진행 중'}</small>
    </aside>
  )

  const handleStart = useCallback((nextFormData: ReportFormData) => {
    setFormData(nextFormData)
    setStep(nextFormData.method === 'live' ? 'live' : 'media')
  }, [])

  const handleBackToSelect = useCallback(() => {
    setStep('select')
  }, [])

  const handleComplete = useCallback((completedFormData: ReportFormData) => {
    setFormData(completedFormData)
    setStep('complete')
  }, [])

  const handleReset = useCallback(() => {
    setFormData(createInitialFormData())
    setStep('select')
  }, [])

  if (step === 'live') {
    return <>{requestBanner}<LiveReportPage formData={formData} onBack={handleBackToSelect} onComplete={handleComplete} /></>
  }

  if (step === 'media') {
    return <>{requestBanner}<MediaReportPage formData={formData} onBack={handleBackToSelect} onComplete={handleComplete} /></>
  }

  if (step === 'complete') {
    return <ReportCompletePage formData={formData} onRestart={handleReset} />
  }

  return <>{requestBanner}<ReportTypePage initialFormData={formData} onStart={handleStart} /></>
}
