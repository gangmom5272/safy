import { useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { PageHeader } from '../../../components/common/PageHeader'
import { formatDateTime } from '../../../utils/dateTime'
import { ErrorState, LoadingSkeleton } from '../situations/SituationStates'
import {
  analysisStatusLabels,
  congestionLevelLabels,
  localPeakCongestionLevel,
  reportTypeLabels,
  shortRiskLevelLabels,
  type AnalysisStatus,
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
          실시간 지도로 돌아가기
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
            실시간 지도로 돌아가기
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
            <h2 id="analysis-title" className="report-detail-section-title">제보 분석</h2>
            <ReportAnalysisPanel
              analysis={analysis}
              isLoading={isAnalysisLoading}
              error={analysisError}
              onRetry={() => void retryAnalysis()}
            />
          </section>
        </>
      ) : null}
    </div>
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
            <div>
              <dt><DetailIcon name="note" />제보내용</dt>
              <dd>
                <p className="report-description detail">
                  {report.description || '상세 설명이 없습니다.'}
                </p>
              </dd>
            </div>
          </dl>
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
      {/* 두 값 모두 이 제보를 분석한 AI 결과다. 지역 단위 서울시 공공데이터는 상황
          요약이 담당하고 여기에는 섞지 않는다. */}
      <dl className="report-detail-list analysis-detail-list">
        <div>
          <dt><DetailIcon name="congestion" />혼잡도</dt>
          {/* 항목명이 이미 '혼잡도'이므로 값에는 단계만 남긴다. */}
          <dd>{formatCongestionValue(analysis)}</dd>
        </div>
        {/* 인원 추정치는 정확도가 보장되지 않아 노출하지 않는다. 내부 판단 용도로만 쓴다. */}
        <div>
          <dt><DetailIcon name="risk" />위험도</dt>
          <dd>{formatRiskValue(analysis)}</dd>
        </div>
      </dl>
      {/* 두 값이 어긋나 보일 수 있어 기준을 밝힌다. 혼잡도는 가장 붐빈 구역만 보므로
          전체가 한산하면 혼잡도가 높고 위험도는 낮게 나오는 조합이 정상이다. */}
      <p className="analysis-source">
        혼잡도는 AI 영상 분석 기준으로 가장 붐빈 구역을 영상 전체에서 집계한 값입니다.
        전체가 한산하면 위험도와 다르게 보일 수 있습니다.
      </p>
      {analysis.status === 'not_assessable' ? (
        <p className="muted-text">프레임 품질이 낮아 분석 결과를 산출하지 못했습니다.</p>
      ) : null}
      {analysis.status === 'pending' ? <p className="muted-text">분석 대기 중입니다.</p> : null}
      {analysis.status === 'processing' ? <p className="muted-text">분석이 진행 중입니다.</p> : null}
      {analysis.status === 'failed' ? (
        <p className="muted-text">{analysis.errorMessage || '분석에 실패했습니다.'}</p>
      ) : null}
    </section>
  )
}

/** 평가 상태가 값 대신 표시할 문구를 정한다. 값이 나올 상태면 null 이다. */
function statusPlaceholder(status: AnalysisStatus) {
  if (status === 'completed') return null
  if (status === 'not_assessable') return '분석 불가'
  if (status === 'processing') return '분석 중'
  if (status === 'failed') return '분석 실패'
  return '분석 대기 중'
}

/** 점수(0~100)는 시민이 해석할 수 없으므로 단계로만 보여준다. */
function formatCongestionValue(analysis: ReportAnalysis) {
  const placeholder = statusPlaceholder(analysis.status)
  if (placeholder) return placeholder

  return typeof analysis.localPeakCongestionScore === 'number'
    ? congestionLevelLabels[localPeakCongestionLevel(analysis.localPeakCongestionScore)]
    : '정보 없음'
}

/**
 * 등급을 앞세우고 점수를 괄호로 보조한다. 점수만 보여주면 시민이 '72점'을 해석할 수 없고,
 * 관리자 콘솔도 등급 라벨을 주고 점수를 배지 안 부가 정보로 둔다.
 */
function formatRiskValue(analysis: ReportAnalysis) {
  const placeholder = statusPlaceholder(analysis.status)
  if (placeholder) return placeholder

  const level = analysis.riskLevel ? shortRiskLevelLabels[analysis.riskLevel] : null
  const score = typeof analysis.riskScore === 'number' ? `${Math.round(analysis.riskScore)}점` : null

  if (level && score) return `${level} (${score})`
  return level ?? score ?? '정보 없음'
}

type DetailIconName = 'address' | 'time' | 'congestion' | 'people' | 'risk' | 'note'

function DetailIcon({ name }: { name: DetailIconName }) {
  const paths = {
    address: <><path d="m4 6 5-2 6 2 5-2v14l-5 2-6-2-5 2Z"/><path d="M9 4v14M15 6v14"/></>,
    time: <><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></>,
    congestion: <><path d="M12 3 3 20h18Z"/><path d="M12 9v4M12 17h.01"/></>,
    people: <><path d="M16 21v-2a4 4 0 0 0-4-4H7a4 4 0 0 0-4 4v2"/><circle cx="9.5" cy="7" r="4"/><path d="M17 11a4 4 0 0 1 4 4v2"/></>,
    risk: <><path d="M12 3l8 3v6c0 5-3.4 7.9-8 9-4.6-1.1-8-4-8-9V6Z"/><path d="M12 8v4M12 15h.01"/></>,
    note: <><path d="M6 3h9l4 4v14H6Z"/><path d="M14 3v5h5M9 12h6M9 16h4"/></>,
  }
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name]}</svg>
}

function ShareIcon() { return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M12 16V3M8 7l4-4 4 4"/><path d="M5 11v9h14v-9"/></svg> }
