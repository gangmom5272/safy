import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { AdminSection } from '../../components/admin/AdminSection'
import { RiskLevelIndicator } from '../../components/admin/RiskLevelIndicator'
import { StatusBadge } from '../../components/admin/StatusBadge'
import { RiskBadge } from '../../components/admin/RiskBadge'
import { reportStatusLabels, reportTypeLabels } from '../../mocks/adminMockData'
import { ROUTE_PATHS } from '../../router/paths'
import type { AdminReport, ReportStatus } from '../../types/admin'
import { formatAdminDate } from '../../utils/adminDate'
import { getReportStatusTone } from '../../utils/adminStatus'
import { getAnalysisStatusLabel } from '../../utils/reportLabels'
import {
  detailToAdminReport,
  getOperatorReportDetail,
  getReportAnalysis,
  toMyOutcome,
  type AssignmentStatus,
  type ReportImage,
  type ReportAnalysis,
} from './reportApi'
import { ReportActionPanel } from './ReportActionPanel'
import { ImageLightbox } from '../../components/admin/ImageLightbox'

export function ReportDetailPage() {
  const { reportId } = useParams()
  const navigate = useNavigate()
  const numericReportId = Number(reportId)
  const isValidReportId = Number.isInteger(numericReportId) && numericReportId > 0

  const [report, setReport] = useState<AdminReport | null>(null)
  const [analysis, setAnalysis] = useState<ReportAnalysis | null>(null)
  const [images, setImages] = useState<ReportImage[]>([])
  const [myOutcome, setMyOutcome] = useState<ReportStatus | null>(null)
  const [myAssignmentStatus, setMyAssignmentStatus] = useState<AssignmentStatus | null>(null)
  const [lightboxImage, setLightboxImage] = useState<{ src: string; alt: string } | null>(null)
  const [loadError, setLoadError] = useState('')
  const [isLoading, setIsLoading] = useState(isValidReportId)

  useEffect(() => {
    if (!isValidReportId) return

    let active = true

    async function load() {
      try {
        const detail = await getOperatorReportDetail(numericReportId)
        if (!active) return
        setReport(detailToAdminReport(detail))
        setImages(detail.images)
        setMyOutcome(toMyOutcome(detail))
        setMyAssignmentStatus(detail.myAssignmentStatus)

        // 분석 API는 아직 준비 중이라 실패해도 상세 화면은 유지한다.
        const loadedAnalysis = await getReportAnalysis(numericReportId).catch(() => null)
        if (active) setAnalysis(loadedAnalysis)
      } catch (error) {
        if (active) setLoadError(error instanceof Error ? error.message : '제보를 불러오지 못했습니다.')
      } finally {
        if (active) setIsLoading(false)
      }
    }

    void load()
    return () => { active = false }
  }, [isValidReportId, numericReportId])

  if (isLoading) {
    return <section className="admin-card"><p role="status">제보를 불러오는 중입니다...</p></section>
  }

  if (!isValidReportId || loadError || !report) {
    return (
      <section className="admin-card empty-state">
        <strong role="alert">
          {!isValidReportId ? '올바르지 않은 제보 번호입니다.' : loadError || '제보를 찾을 수 없습니다'}
        </strong>
        <Link className="primary-button" to={ROUTE_PATHS.REPORTS}>제보 목록으로 이동</Link>
      </section>
    )
  }

  const previewImages = images.flatMap((image) => image.previewUrl
    ? [{ id: image.reportImageId, url: image.previewUrl }]
    : [])
  const riskLevel = analysis?.riskLevel ?? report.riskLevel
  const summary = analysis?.summary ?? report.aiSummary
  const confidence = analysis?.confidence ?? null

  return (
    <section className="page-stack report-detail-page">
      <nav className="breadcrumb" aria-label="제보 상세 경로">
        <Link to={ROUTE_PATHS.REPORTS}>제보 목록</Link><span aria-hidden="true">/</span>
        <span>제보 상세</span><span aria-hidden="true">/</span>
        <strong aria-current="page">제보 아이디 #{report.id}</strong>
      </nav>

      <div className="report-detail-grid report-detail-grid--first">
        <AdminSection title="제보 이미지">
          {previewImages.length > 0 ? (
            <div className="report-image-gallery">
              {previewImages.map((image, index) => {
                const alt = `제보 #${report.id} 현장 이미지 ${index + 1}`
                return (
                  <button
                    key={image.id}
                    type="button"
                    className="report-image-thumb"
                    aria-label={`${alt} 크게 보기`}
                    onClick={() => setLightboxImage({ src: image.url, alt })}
                  >
                    <img className="report-image" src={image.url} alt={alt} />
                  </button>
                )
              })}
            </div>
          ) : (
            <div className="report-image-placeholder" role="img" aria-label={`제보 #${report.id} 이미지 없음`}>
              <span>{report.isLive ? '라이브 제보입니다.' : '제보 이미지가 없습니다.'}</span>
            </div>
          )}
        </AdminSection>

        <AdminSection title="제보 정보 요약">
          <dl className="report-summary">
            <div><dt>제보 ID</dt><dd>#{report.id}</dd></div>
            <div><dt>제보 형태</dt><dd>{report.isLive ? '라이브' : '이미지'}</dd></div>
            <div><dt>카테고리</dt><dd>{reportTypeLabels[report.type]}</dd></div>
            <div><dt>제보 시간</dt><dd>{formatAdminDate(report.reportedAt)}</dd></div>
            <div className="report-summary__group">
              <dt>위험 정보</dt>
              <dd><span>위험도</span><RiskBadge value={riskLevel} score={report.riskScore} /></dd>
              {/* 추정 인원(crowdCount)은 정확도가 보장되지 않아 노출하지 않는다. */}
              <dd><span>분석 상태</span><span>{getAnalysisStatusLabel(analysis?.status) ?? '-'}</span></dd>
            </div>
            <div className="report-summary__group">
              <dt>처리 정보</dt>
              {myOutcome && (
                <dd>
                  <span>내 처리 결과</span>
                  <StatusBadge tone={getReportStatusTone(myOutcome)}>{reportStatusLabels[myOutcome]}</StatusBadge>
                </dd>
              )}
              <dd><span>제보 상태</span><StatusBadge tone={getReportStatusTone(report.status)}>{reportStatusLabels[report.status]}</StatusBadge></dd>
              <dd><span>관제센터</span><strong>{report.controlCenterName || '미배정'}</strong></dd>
            </div>
            <div className="report-summary__address"><dt>주소</dt><dd>{report.location}</dd></div>
          </dl>
        </AdminSection>
      </div>

      <div className="report-detail-grid report-detail-grid--second">
        <AdminSection title="위험도 지표">
          <RiskLevelIndicator level={riskLevel} score={report.riskScore} />
        </AdminSection>

        <AdminSection title="제보 처리">
          <ReportActionPanel
            reportId={numericReportId}
            assignmentStatus={myAssignmentStatus}
            onActionComplete={() => navigate(ROUTE_PATHS.REPORTS, { replace: true })}
          />
        </AdminSection>
      </div>

      <AdminSection title="AI 상황 요약">
        <div className="ai-summary-layout">
          <div className="ai-summary-metrics">
            <div><span>위험 단계</span><RiskBadge value={riskLevel} score={report.riskScore} /></div>
            <div><span>신뢰도</span><strong>{confidence === null ? '-' : `${Math.round(confidence * 100)}%`}</strong></div>
          </div>
          <section>
            <h3>상황 요약</h3>
            <p className="break-text">{summary || 'AI 분석 결과가 아직 없습니다.'}</p>
          </section>
          <section>
            <h3>분석 시각</h3>
            <p>{analysis?.analyzedAt ? formatAdminDate(analysis.analyzedAt) : '-'}</p>
          </section>
        </div>
      </AdminSection>

      {lightboxImage && (
        <ImageLightbox
          src={lightboxImage.src}
          alt={lightboxImage.alt}
          onClose={() => setLightboxImage(null)}
        />
      )}
    </section>
  )
}
