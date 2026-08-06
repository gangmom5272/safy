import { useEffect, useState, type ReactNode } from 'react'
import { Link, useNavigate, useOutletContext, useParams } from 'react-router'
import { RiskLevelIndicator } from '../../components/admin/RiskLevelIndicator'
import { reportTypeLabels } from '../../mocks/adminMockData'
import { ROUTE_PATHS } from '../../router/paths'
import type { AdminReport } from '../../types/admin'
import { formatAdminDate } from '../../utils/adminDate'
import { getSubcategoryLabel } from '../../utils/reportLabels'
import {
  detailToAdminReport,
  getOperatorReportDetail,
  getReportAnalysis,
  getReportHandlingHistory,
  type AssignmentStatus,
  type ReportHandlingHistory,
  type ReportImage,
  type ReportAnalysis,
} from './reportApi'
import { ReportActionPanel } from './ReportActionPanel'
import { ReportProgressTimeline } from './ReportProgressTimeline'
import { toReportProgressSteps } from './reportProgress'
import { ImageLightbox } from '../../components/admin/ImageLightbox'
import type { AdminLayoutOutletContext } from '../../layouts/AdminLayout'

function ReportImageIcon() {
  return (
    <svg className="report-card__icon" viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false">
      <rect x="3" y="4" width="18" height="16" rx="2" />
      <circle cx="8.5" cy="9.5" r="1.5" />
      <path d="M4 17l5-5 4 4 3-3 4 4" />
    </svg>
  )
}

function ReportContentIcon() {
  return (
    <svg className="report-card__icon" viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false">
      <path d="M6 3h8l4 4v14H6z" />
      <path d="M14 3v5h5M9 13h6M9 17h6" />
    </svg>
  )
}

function ReportInfoIcon() {
  return (
    <svg className="report-card__icon" viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false">
      <rect x="4" y="3" width="16" height="18" rx="2" />
      <path d="M8 8h8M8 12h8M8 16h5" />
    </svg>
  )
}

function ReportRiskIcon() {
  return (
    <svg className="report-card__icon" viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false">
      <path d="M12 3l7 3v6c0 4.2-2.8 7.9-7 9-4.2-1.1-7-4.8-7-9V6z" />
      <path d="M12 9v4M12 16h.01" />
    </svg>
  )
}

function ReportHandlingIcon() {
  return (
    <svg className="report-card__icon" viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false">
      <circle cx="9" cy="8" r="3.2" />
      <path d="M3.5 19a5.5 5.5 0 0 1 11 0" />
      <path d="M16.5 6.5a3 3 0 0 1 0 5.6M18 19a5.6 5.6 0 0 0-2.2-4.4" />
    </svg>
  )
}

function ReportHistoryIcon() {
  return (
    <svg className="report-card__icon" viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false">
      <circle cx="12" cy="12" r="8.5" />
      <path d="M12 7.2V12l3.4 2" />
    </svg>
  )
}

function ChevronIcon({ direction }: { direction: 'prev' | 'next' }) {
  return (
    <svg className="report-image-icon" viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false">
      <path d={direction === 'prev' ? 'M15 5l-7 7 7 7' : 'M9 5l7 7-7 7'} />
    </svg>
  )
}

interface DetailCardProps {
  title: string
  icon: ReactNode
  className: string
  children: ReactNode
}

/** 상세 화면 카드 골격. 제목을 카드 안에 두어 3열 배치에서 열 높이가 흐트러지지 않게 한다. */
function DetailCard({ title, icon, className, children }: DetailCardProps) {
  return (
    <section className={`admin-card report-card ${className}`}>
      <h2 className="report-card__title">{icon}{title}</h2>
      {children}
    </section>
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
  const [myAssignmentStatus, setMyAssignmentStatus] = useState<AssignmentStatus | null>(null)
  const [assignedOperatorName, setAssignedOperatorName] = useState<string | null>(null)
  const [handlingHistory, setHandlingHistory] = useState<ReportHandlingHistory | null>(null)
  const [isImageOpen, setIsImageOpen] = useState(false)
  const [imageIndex, setImageIndex] = useState(0)
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
        setMyAssignmentStatus(detail.myAssignmentStatus)
        setAssignedOperatorName(detail.assignedOperatorName)

        // 분석·처리 이력은 실패해도 상세 화면 자체는 유지한다. 서로 독립이라 함께 받는다.
        const [loadedAnalysis, loadedHistory] = await Promise.all([
          getReportAnalysis(numericReportId).catch(() => null),
          getReportHandlingHistory(numericReportId).catch(() => null),
        ])
        if (!active) return
        setAnalysis(loadedAnalysis)
        setHandlingHistory(loadedHistory)
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
  // 다른 제보로 이동하면 목록이 짧아질 수 있어 항상 범위 안으로 접어 읽는다.
  const currentIndex = Math.min(imageIndex, Math.max(previewImages.length - 1, 0))
  const currentImage = previewImages[currentIndex]
  const currentImageAlt = `제보 #${report.id} 현장 이미지 ${currentIndex + 1}`
  const moveImage = (step: number) => setImageIndex(
    (currentIndex + step + previewImages.length) % previewImages.length,
  )
  const progressSteps = handlingHistory ? toReportProgressSteps(handlingHistory) : []

  return (
    <section className="page-stack report-detail-page">
      <nav className="breadcrumb" aria-label="제보 상세 경로">
        <Link to={ROUTE_PATHS.REPORTS}>제보 목록</Link><span aria-hidden="true">/</span>
        <span>제보 상세</span><span aria-hidden="true">/</span>
        <strong aria-current="page">제보 아이디 #{report.id}</strong>
      </nav>

      {/* 카드 순서는 좁은 화면에서의 읽기 순서(이미지 → 기본 정보 → 내용 → 위험도 → 처리)에
          맞추고, 넓은 화면의 3열 배치는 grid-template-areas 가 담당한다. */}
      <div className="report-detail-layout">
        <DetailCard className="report-detail-layout__image" title="제보 이미지" icon={<ReportImageIcon />}>
          {currentImage ? (
            <>
              {/* 한 장씩 원래 크기로 보여주고 좌우 버튼과 아래 썸네일로 넘긴다.
                  이미지를 누르면 확대되고, 확대 상태에서도 좌우로 넘길 수 있다. */}
              <div className="report-image-frame">
                {/* 좌우 버튼의 기준. 썸네일 줄을 뺀 이미지 영역이라 버튼이 이미지
                    한가운데 높이에 놓인다. */}
                <div className="report-image-stage">
                  <button
                    type="button"
                    className="report-image-open"
                    aria-label={`${currentImageAlt} 크게 보기`}
                    onClick={() => setIsImageOpen(true)}
                  >
                    <img className="report-image" src={currentImage.url} alt={currentImageAlt} />
                  </button>
                  {previewImages.length > 1 && (
                    <>
                      <button
                        type="button"
                        className="report-image-action report-image-action--prev"
                        aria-label="이전 이미지 보기"
                        onClick={() => moveImage(-1)}
                      >
                        <ChevronIcon direction="prev" />
                      </button>
                      <button
                        type="button"
                        className="report-image-action report-image-action--next"
                        aria-label="다음 이미지 보기"
                        onClick={() => moveImage(1)}
                      >
                        <ChevronIcon direction="next" />
                      </button>
                    </>
                  )}
                </div>
                {/* 썸네일도 회색 프레임 안에 둔다. 카드 흰 배경에 두면 이미지 영역과
                    분리돼 보인다. */}
                {previewImages.length > 1 && (
                  <div className="report-image-thumbs">
                    {previewImages.map((image, index) => (
                      <button
                        key={image.id}
                        type="button"
                        className={index === currentIndex ? 'report-image-thumb is-active' : 'report-image-thumb'}
                        aria-label={`${index + 1}번째 이미지 보기`}
                        aria-current={index === currentIndex ? 'true' : undefined}
                        onClick={() => setImageIndex(index)}
                      >
                        <img src={image.url} alt="" />
                      </button>
                    ))}
                  </div>
                )}
              </div>
            </>
          ) : (
            <div className="report-image-placeholder" role="img" aria-label={`제보 #${report.id} 이미지 없음`}>
              <span>{report.isLive ? '라이브 제보입니다.' : '제보 이미지가 없습니다.'}</span>
            </div>
          )}
        </DetailCard>

        <DetailCard className="report-detail-layout__info" title="기본 정보" icon={<ReportInfoIcon />}>
          {/* 한 dl 로 두어야 카드가 늘어날 때 행이 고르게 벌어진다. */}
          <dl className="report-info">
            <div><dt>제보 ID</dt><dd>#{report.id}</dd></div>
            <div>
              <dt>제보 형태</dt>
              <dd>
                {report.isLive && <span className="report-info__live-dot" aria-hidden="true" />}
                {report.isLive ? '라이브' : '이미지'}
              </dd>
            </div>
            <div>
              <dt>카테고리</dt>
              {/* 세부 분류는 서버가 enum 이름(OTHER_SITUATION 등)을 그대로 주므로
                  반드시 한글 라벨로 바꿔서 붙인다. 세부 분류가 없으면 상위만 남긴다. */}
              <dd>{[reportTypeLabels[report.type], getSubcategoryLabel(report.subcategory)].filter(Boolean).join(' · ')}</dd>
            </div>
            <div><dt>제보 시간</dt><dd>{formatAdminDate(report.reportedAt)}</dd></div>
            <div>
              <dt>분석 시간</dt>
              <dd>{analysis?.analyzedAt ? formatAdminDate(analysis.analyzedAt) : '-'}</dd>
            </div>
            {/* 담당 관제센터는 처리 정보 카드에서만 보여준다. */}
            <div><dt>주소</dt><dd>{report.location}</dd></div>
          </dl>
        </DetailCard>

        <DetailCard className="report-detail-layout__content" title="제보 내용" icon={<ReportContentIcon />}>
          <p className="report-content break-text">{summary || '제보 내용이 없습니다.'}</p>
        </DetailCard>

        <DetailCard className="report-detail-layout__risk" title="위험도 지표" icon={<ReportRiskIcon />}>
          <RiskLevelIndicator level={riskLevel} score={riskScore} />
        </DetailCard>

        {/* 오른쪽 열은 격자 행에 묶지 않고 한 덩어리로 쌓는다. 종결된 제보라 처리
            버튼이 사라져도 아래 '처리 정보'가 그만큼 자동으로 늘어난다. */}
        <div className="report-detail-layout__side">
          <DetailCard className="report-detail-side__process" title="제보 처리" icon={<ReportHandlingIcon />}>
            <ReportActionPanel
              reportId={numericReportId}
              assignmentStatus={myAssignmentStatus}
              onActionComplete={() => navigate(ROUTE_PATHS.REPORTS, { replace: true })}
            />
          </DetailCard>

          <DetailCard className="report-detail-side__handling" title="처리 정보" icon={<ReportHistoryIcon />}>
            {/* 현재 상태와 처리 결과는 아래 타임라인이 이미 보여주므로 넣지 않는다. */}
            <dl className="report-info report-info--summary">
              <div><dt>소속</dt><dd>{report.controlCenterName || '-'}</dd></div>
              <div><dt>담당자</dt><dd>{assignedOperatorName || '-'}</dd></div>
            </dl>

            {/* 이력은 이 칸 안에서만 스크롤한다. 칸 자체가 카드 높이를 밀지 않아야
                가운데 열(위험도까지)의 높이가 그대로 유지된다. */}
            <div className="report-history-scroll">
              {progressSteps.length > 0
                ? <ReportProgressTimeline steps={progressSteps} />
                : <p className="empty-state" role="status">처리 이력을 불러오지 못했습니다.</p>}
            </div>
          </DetailCard>
        </div>
      </div>

      {/* 확대 보기도 카드와 같은 currentIndex 를 쓴다. 확대 상태에서 넘긴 장이
          닫은 뒤 카드에도 그대로 남는다. */}
      {isImageOpen && currentImage && (
        <ImageLightbox
          src={currentImage.url}
          alt={currentImageAlt}
          onClose={() => setIsImageOpen(false)}
          onPrev={previewImages.length > 1 ? () => moveImage(-1) : undefined}
          onNext={previewImages.length > 1 ? () => moveImage(1) : undefined}
          counter={previewImages.length > 1
            ? `${currentIndex + 1} / ${previewImages.length}`
            : undefined}
        />
      )}
    </section>
  )
}
