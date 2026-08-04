import { useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { PageHeader } from '../../../components/common/PageHeader'
import { formatDateTime } from '../../../utils/dateTime'
import { ErrorState, LoadingSkeleton } from '../situations/SituationStates'
import { AreaMap } from '../situations/AreaMap'
import {
  analysisStatusLabels,
  reportTypeLabels,
  riskLevelLabels,
  type ReportAnalysis,
  type ReportDetail,
} from '../situations/types'
import { useReportDetail } from '../situations/useReportDetail'
import '../situations/SituationsPage.css'
import './ReportDetailPage.css'

export function ReportDetailPage() {
  const { reportId } = useParams()
  const navigate = useNavigate()
  const [imageFailed, setImageFailed] = useState(false)
  const [shareMessage, setShareMessage] = useState('')
  const {
    report,
    isReportLoading,
    reportError,
    retryReport,
    analysis,
    isAnalysisLoading,
    analysisError,
    retryAnalysis,
  } = useReportDetail(reportId)

  const shareReport = async () => {
    if (!report) {
      return
    }

    try {
      const url = `${window.location.origin}/app/reports/${report.id}`

      if (navigator.share) {
        await navigator.share({
          title: report.title,
          text: `${report.locationName} ${report.description ?? ''}`.trim(),
          url,
        })
      } else if (navigator.clipboard) {
        await navigator.clipboard.writeText(url)
      } else {
        window.prompt('공유 링크를 복사해 주세요.', url)
      }

      setShareMessage('공유 링크가 준비되었습니다.')
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') {
        return
      }

      setShareMessage('공유를 완료하지 못했습니다.')
    }

    window.setTimeout(() => setShareMessage(''), 2200)
  }

  if (!reportId) {
    return (
      <div className="situation-page">
        <ErrorState message="해당 제보 정보를 찾을 수 없습니다." />
        <button className="inline-action-button" type="button" onClick={() => navigate('/app/situations')}>
          상황 확인으로 돌아가기
        </button>
      </div>
    )
  }

  return (
    <div className="situation-page report-detail-page">
      <PageHeader title="실시간 제보 상세" onBack={() => navigate(-1)} action={<button className="report-detail-share" type="button" aria-label="제보 공유하기" onClick={shareReport} disabled={!report}><ShareIcon /></button>} />

      {isReportLoading && !report ? <LoadingSkeleton /> : null}
      {reportError && !report ? (
        <>
          <ErrorState
            message="해당 제보 정보를 찾을 수 없습니다."
            actionLabel="다시 시도"
            onAction={() => void retryReport()}
          />
          <button className="inline-action-button" type="button" onClick={() => navigate('/app/situations')}>
            상황 확인으로 돌아가기
          </button>
        </>
      ) : null}
      {report ? (
        <>
          <ReportDetailSummary
            report={report}
            imageFailed={imageFailed}
            onImageError={() => setImageFailed(true)}
          />
          {shareMessage ? <p className="share-feedback">{shareMessage}</p> : null}
          <section className="report-detail-content-section" aria-labelledby="analysis-title">
            <h2 id="analysis-title" className="report-detail-section-title">현장 상황 분석</h2>
            <ReportAnalysisPanel
              analysis={analysis}
              isLoading={isAnalysisLoading}
              error={analysisError}
              onRetry={() => void retryAnalysis()}
            />
          </section>
          <ReportLocationMap report={report} />
        </>
      ) : null}
    </div>
  )
}

function ReportLocationMap({ report }: { report: ReportDetail }) {
  const latitude = report.latitude ?? 37.4979
  const longitude = report.longitude ?? 127.0276

  return (
    <section className="report-detail-content-section" aria-labelledby="report-map-title">
      <h2 id="report-map-title" className="report-detail-section-title">주변 위험 지도</h2>
      <AreaMap
        variant="report-detail"
        showTitle={false}
        locationName={report.locationName}
        isLoading={false}
        error={null}
        mapData={{
        center: { latitude, longitude },
        zones: [
          {
            id: `${report.id}-zone`,
            name: '제보 위치 주변',
            level: report.riskLevel,
            coordinates: [{ latitude, longitude }],
          },
        ],
        reportMarkers: [
          {
            reportId: report.id,
            latitude,
            longitude,
            locationName: report.locationName,
            riskLevel: report.riskLevel,
          },
        ],
        }}
      />
    </section>
  )
}

type ReportDetailSummaryProps = {
  report: ReportDetail
  imageFailed: boolean
  onImageError: () => void
}

function ReportDetailSummary({ report, imageFailed, onImageError }: ReportDetailSummaryProps) {
  const mediaUrl = report.maskedMediaUrl ?? report.maskedThumbnailUrl

  return (
    <section className="report-detail-summary" aria-labelledby="report-detail-title">
      <div className="report-detail-media">
        {mediaUrl && !imageFailed ? (
          <img src={mediaUrl} alt={`${report.title} 마스킹 처리 미디어`} onError={onImageError} />
        ) : (
          <div className="thumbnail-placeholder" aria-label="마스킹 처리 미디어 없음">
            <span>MASKED</span>
          </div>
        )}
      </div>
      <div className="report-detail-content-section">
        <h2 id="report-detail-title" className="report-detail-section-title">{report.locationName || report.title}</h2>
        <div className="report-detail-card report-detail-body">
          <div className="report-card-badges">
            <span className={`risk-badge risk-${report.riskLevel}`}>
              {riskLevelLabels[report.riskLevel]}
            </span>
          </div>
          <p className="report-category">{reportTypeLabels[report.type]}</p>
          <dl className="report-detail-list">
            <div>
              <dt><DetailIcon name="address" />주소</dt>
              <dd>{report.address || '주소 정보 없음'}</dd>
            </div>
            <div className="report-date-row">
              <dt><DetailIcon name="time" />제보 시각</dt>
              <dd>{formatDateTime(report.reportedAt)}</dd>
            </div>
          </dl>
          <p className="report-description detail">
            {report.description || '상세 설명이 없습니다.'}
          </p>
        </div>
      </div>
    </section>
  )
}

type ReportAnalysisPanelProps = {
  analysis: ReportAnalysis | null | undefined
  isLoading: boolean
  error: unknown
  onRetry: () => void
}

function ReportAnalysisPanel({ analysis, isLoading, error, onRetry }: ReportAnalysisPanelProps) {
  if (isLoading && !analysis) {
    return <section className="report-detail-card"><LoadingSkeleton /></section>
  }

  if (error) {
    return (
      <section className="report-detail-card"><ErrorState
        message="현장 분석 결과를 불러오지 못했습니다."
        actionLabel="다시 시도"
        onAction={onRetry}
      /></section>
    )
  }

  if (!analysis) {
    return (
      <section className="report-detail-card">
        <p className="muted-text">아직 현장 분석 결과가 없습니다.</p>
      </section>
    )
  }

  return (
    <section className="report-detail-card">
      <div className="analysis-status-row">
        {analysis.status === 'completed' ? <span className="analysis-complete-badge">{analysisStatusLabels[analysis.status]}</span> : <span />}
        {analysis.analyzedAt ? <span className="analysis-time">{formatDateTime(analysis.analyzedAt)}</span> : null}
      </div>
      {analysis.status === 'completed' ? (
        <dl className="report-detail-list analysis-detail-list">
          <div>
            <dt><DetailIcon name="congestion" />혼잡도</dt>
            <dd>
              {analysis.congestionLevel ? riskLevelLabels[analysis.congestionLevel] : '정보 없음'}
            </dd>
          </div>
          {/* 인원 추정치는 정확도가 보장되지 않아 노출하지 않는다. 내부 판단 용도로만 쓴다. */}
          <div>
            <dt><DetailIcon name="ai" />AI 요약</dt>
            <dd>{analysis.aiSummary || '요약 정보 없음'}</dd>
          </div>
        </dl>
      ) : null}
      {analysis.status === 'pending' ? <p className="muted-text">분석 대기 중입니다.</p> : null}
      {analysis.status === 'processing' ? <p className="muted-text">분석이 진행 중입니다.</p> : null}
      {analysis.status === 'failed' ? (
        <p className="muted-text">{analysis.errorMessage || '분석에 실패했습니다.'}</p>
      ) : null}
    </section>
  )
}

type DetailIconName = 'address' | 'time' | 'congestion' | 'people' | 'ai'

function DetailIcon({ name }: { name: DetailIconName }) {
  const paths = {
    address: <><path d="m4 6 5-2 6 2 5-2v14l-5 2-6-2-5 2Z"/><path d="M9 4v14M15 6v14"/></>,
    time: <><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></>,
    congestion: <><path d="M12 3 3 20h18Z"/><path d="M12 9v4M12 17h.01"/></>,
    people: <><path d="M16 21v-2a4 4 0 0 0-4-4H7a4 4 0 0 0-4 4v2"/><circle cx="9.5" cy="7" r="4"/><path d="M17 11a4 4 0 0 1 4 4v2"/></>,
    ai: <><rect x="3" y="5" width="18" height="14" rx="4"/><path d="M8 15V9l2 6V9M14 9v6M17 9v6M12 2v3"/></>,
  }
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name]}</svg>
}

function ShareIcon() { return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M12 16V3M8 7l4-4 4 4"/><path d="M5 11v9h14v-9"/></svg> }
