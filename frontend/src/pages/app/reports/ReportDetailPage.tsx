import { useState } from 'react'
import { useNavigate, useParams } from 'react-router'
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
      <header className="report-detail-header">
        <button type="button" onClick={() => navigate(-1)}>
          뒤로
        </button>
        <h1>실시간 제보 상세</h1>
        <button type="button" onClick={shareReport} disabled={!report}>
          공유
        </button>
      </header>

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
          <ReportAnalysisPanel
            analysis={analysis}
            isLoading={isAnalysisLoading}
            error={analysisError}
            onRetry={() => void retryAnalysis()}
          />
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
    <AreaMap
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
    <section className="report-detail-card" aria-labelledby="report-detail-title">
      <div className="report-detail-media">
        {mediaUrl && !imageFailed ? (
          <img src={mediaUrl} alt={`${report.title} 마스킹 처리 미디어`} onError={onImageError} />
        ) : (
          <div className="thumbnail-placeholder" aria-label="마스킹 처리 미디어 없음">
            <span>MASKED</span>
          </div>
        )}
      </div>
      <div className="report-detail-body">
        <div className="report-card-badges">
          <span className={`risk-badge risk-${report.riskLevel}`}>
            {riskLevelLabels[report.riskLevel]}
          </span>
          {report.isWebRtc ? <span className="webrtc-tag static">WebRTC 제보</span> : null}
        </div>
        <p className="report-category">{reportTypeLabels[report.type]}</p>
        <h2 id="report-detail-title">{report.title}</h2>
        <dl className="report-detail-list">
          <div>
            <dt>위치</dt>
            <dd>{report.locationName}</dd>
          </div>
          <div>
            <dt>주소</dt>
            <dd>{report.address || '주소 정보 없음'}</dd>
          </div>
          <div>
            <dt>제보 시각</dt>
            <dd>{formatDateTime(report.reportedAt)}</dd>
          </div>
        </dl>
        <p className="report-description detail">
          {report.description || '상세 설명이 없습니다.'}
        </p>
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
    return <LoadingSkeleton />
  }

  if (error) {
    return (
      <ErrorState
        message="현장 분석 결과를 불러오지 못했습니다."
        actionLabel="다시 시도"
        onAction={onRetry}
      />
    )
  }

  if (!analysis) {
    return (
      <section className="report-detail-card">
        <h2>현장 상황 분석</h2>
        <p className="muted-text">아직 현장 분석 결과가 없습니다.</p>
      </section>
    )
  }

  return (
    <section className="report-detail-card" aria-labelledby="analysis-title">
      <h2 id="analysis-title">현장 상황 분석</h2>
      <div className="analysis-status-row">
        <span className={`risk-badge risk-${analysis.congestionLevel ?? 'low'}`}>
          {analysisStatusLabels[analysis.status]}
        </span>
        {analysis.analyzedAt ? <span>{formatDateTime(analysis.analyzedAt)}</span> : null}
      </div>
      {analysis.status === 'completed' ? (
        <dl className="report-detail-list">
          <div>
            <dt>혼잡도</dt>
            <dd>
              {analysis.congestionLevel ? riskLevelLabels[analysis.congestionLevel] : '정보 없음'}
            </dd>
          </div>
          <div>
            <dt>인원 추정치</dt>
            <dd>
              {typeof analysis.estimatedPeopleCount === 'number'
                ? `${analysis.estimatedPeopleCount.toLocaleString()}명`
                : '정보 없음'}
            </dd>
          </div>
          <div>
            <dt>AI 요약</dt>
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

function formatDateTime(value: string) {
  const date = new Date(value)

  if (Number.isNaN(date.getTime())) {
    return value || '정보 없음'
  }

  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}
