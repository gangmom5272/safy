import { useEffect, useState } from 'react'
import { Link, useNavigate, useOutletContext, useParams } from 'react-router'
import { AdminSection } from '../../components/admin/AdminSection'
import { RiskLevelIndicator } from '../../components/admin/RiskLevelIndicator'
import { StatusBadge } from '../../components/admin/StatusBadge'
import { RiskBadge } from '../../components/admin/RiskBadge'
import { reportStatusLabels, reportTypeLabels } from '../../mocks/adminMockData'
import { ROUTE_PATHS } from '../../router/paths'
import type { AdminReport, ReportStatus } from '../../types/admin'
import { formatAdminDate } from '../../utils/adminDate'
import { getReportStatusTone } from '../../utils/adminStatus'
import { getAnalysisStatusLabel, getSubcategoryLabel } from '../../utils/reportLabels'
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
import type { AdminLayoutOutletContext } from '../../layouts/AdminLayout'

function ReportContentIcon() {
  return (
    <svg className="report-summary__content-icon" viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false">
      <path d="M6 3h8l4 4v14H6z" />
      <path d="M14 3v5h5M9 13h6M9 17h6" />
    </svg>
  )
}

export function ReportDetailPage() {
  const { reportId } = useParams()
  const navigate = useNavigate()
  const { markReportChecked } = useOutletContext<AdminLayoutOutletContext>()
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
        markReportChecked(numericReportId)
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
  }, [isValidReportId, markReportChecked, numericReportId])

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
  // 상세 조회 응답에는 위험도가 없다. 점수는 분석 API(최종 위험 점수)에서만 온다.
  const riskScore = analysis?.riskScore ?? report.riskScore
  const summary = analysis?.summary ?? report.aiSummary
  // '내 처리 결과'와 '제보 상태'를 한 줄로 합친 값이다. 내 배정이 끝났으면 그 결과가
  // 더 정확하다(재배정으로 넘긴 건은 제보 상태만 보면 '확인 중'으로 보여 내가 넘겼다는
  // 사실이 사라진다). 아직 끝나지 않았으면 결과가 없으므로 제보 상태를 쓴다.
  // 목록의 '처리 상태' 열도 같은 기준(reportApi 의 toAssignmentOutcome)이라 두 화면
  // 값이 어긋나지 않는다.
  const handlingOutcome = myOutcome ?? report.status

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
            <div className="report-summary__identity"><dt>제보 ID</dt><dd>#{report.id}</dd></div>
            <div className="report-summary__identity"><dt>제보 형태</dt><dd>{report.isLive ? '라이브' : '이미지'}</dd></div>
            <div className="report-summary__identity">
              <dt>카테고리</dt>
              {/* 세부 분류는 서버가 enum 이름(OTHER_SITUATION 등)을 그대로 주므로
                  반드시 한글 라벨로 바꿔서 붙인다. 세부 분류가 없으면 상위만 남긴다. */}
              <dd>{[reportTypeLabels[report.type], getSubcategoryLabel(report.subcategory)].filter(Boolean).join(' · ')}</dd>
            </div>
            <div className="report-summary__time"><dt>제보 시간</dt><dd>{formatAdminDate(report.reportedAt)}</dd></div>
            <div className="report-summary__time">
              <dt>분석 시간</dt>
              <dd>{analysis?.analyzedAt ? formatAdminDate(analysis.analyzedAt) : '-'}</dd>
            </div>
            <div className="report-summary__group">
              <dt>위험 정보</dt>
              <dd><span>위험도</span><RiskBadge value={riskLevel} score={riskScore} /></dd>
              {/* 추정 인원(crowdCount)은 정확도가 보장되지 않아 노출하지 않는다. */}
              <dd><span>분석 상태</span><span>{getAnalysisStatusLabel(analysis?.status) ?? '-'}</span></dd>
            </div>
            <div className="report-summary__group">
              <dt>처리 정보</dt>
              <dd>
                <span>처리 결과</span>
                <StatusBadge tone={getReportStatusTone(handlingOutcome)}>{reportStatusLabels[handlingOutcome]}</StatusBadge>
              </dd>
              <dd><span>관제센터</span><strong>{report.controlCenterName || '미배정'}</strong></dd>
            </div>
            <div className="report-summary__wide"><dt>주소</dt><dd>{report.location}</dd></div>
            <div className="report-summary__wide">
              <dt className="report-summary__title"><ReportContentIcon />제보 내용</dt>
              <dd className="break-text">{summary || '제보 내용이 없습니다.'}</dd>
            </div>
          </dl>
        </AdminSection>
      </div>

      <div className="report-detail-grid report-detail-grid--second">
        <AdminSection title="위험도 지표">
          <RiskLevelIndicator level={riskLevel} score={riskScore} />
        </AdminSection>

        <AdminSection title="제보 처리">
          <ReportActionPanel
            reportId={numericReportId}
            assignmentStatus={myAssignmentStatus}
            onActionComplete={() => navigate(ROUTE_PATHS.REPORTS, { replace: true })}
          />
        </AdminSection>
      </div>

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
