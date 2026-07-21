import { useCallback, useState } from 'react'
import { DEFAULT_REPORT_CATEGORY, DEFAULT_REPORT_METHOD } from './reportConstants'
import { LiveReportPage } from './LiveReportPage'
import { MediaReportPage } from './MediaReportPage'
import { ReportCompletePage } from './ReportCompletePage'
import { ReportTypePage } from './ReportTypePage'
import type { ReportFormData, ReportStep } from './reportTypes'
import './report-create.css'

function createInitialFormData(): ReportFormData {
  return {
    method: DEFAULT_REPORT_METHOD,
    category: DEFAULT_REPORT_CATEGORY,
    location: null,
  }
}

export function ReportCreatePage() {
  const [step, setStep] = useState<ReportStep>('select')
  const [formData, setFormData] = useState<ReportFormData>(() => createInitialFormData())

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
    return <LiveReportPage formData={formData} onBack={handleBackToSelect} onComplete={handleComplete} />
  }

  if (step === 'media') {
    return <MediaReportPage formData={formData} onBack={handleBackToSelect} onComplete={handleComplete} />
  }

  if (step === 'complete') {
    return <ReportCompletePage formData={formData} onRestart={handleReset} />
  }

  return <ReportTypePage initialFormData={formData} onStart={handleStart} />
}
