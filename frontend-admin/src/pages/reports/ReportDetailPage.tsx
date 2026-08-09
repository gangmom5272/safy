import { useEffect, useState, type ReactNode } from 'react'
import { Link, useNavigate, useOutletContext, useParams } from 'react-router'
import { RiskLevelIndicator } from '../../components/admin/RiskLevelIndicator'
import { reportTypeLabels } from '../../mocks/adminMockData'
import { ROUTE_PATHS } from '../../router/paths'
import type { AdminReport } from '../../types/admin'
import { formatAdminDate } from '../../utils/adminDate'
import { getRecordingHint, getSubcategoryLabel } from '../../utils/reportLabels'
import {
  detailToAdminReport,
  getOperatorReportDetail,
  getReportAnalysis,
  getReportHandlingHistory,
  type AssignmentStatus,
  type RecordingStatus,
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
  /** 제목 오른쪽에 붙는 조작부. 없으면 제목만 그대로 둔다. */
  action?: ReactNode
  children: ReactNode
}

/** 상세 화면 카드 골격. 제목을 카드 안에 두어 3열 배치에서 열 높이가 흐트러지지 않게 한다. */
function DetailCard({ title, icon, className, action, children }: DetailCardProps) {
  return (
    <section className={`admin-card report-card ${className}`}>
      {action ? (
        <div className="report-card__header">
          <h2 className="report-card__title">{icon}{title}</h2>
          {action}
        </div>
      ) : (
        <h2 className="report-card__title">{icon}{title}</h2>
      )}
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
  // 라이브 제보는 추출된 이미지와 원본 녹화 영상이 둘 다 남는다. 한 칸에서 골라 본다.
  const [recording, setRecording] = useState<{ status: RecordingStatus | null; url: string | null }>(
    { status: null, url: null },
  )
  const [mediaTab, setMediaTab] = useState<'image' | 'video'>('image')
  const [videoError, setVideoError] = useState('')
  const [loadError, setLoadError] = useState('')
  const [isLoading, setIsLoading] = useState(isValidReportId)
  // 승인 후 상세를 다시 읽어 상태·타임라인을 갱신한다. 승인은 종결이 아니라 화면에 남는다.
  const [refreshKey, setRefreshKey] = useState(0)

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
        setRecording({ status: detail.recordingStatus, url: detail.originalVideoUrl })

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
  }, [isValidReportId, markReportChecked, numericReportId, refreshKey])

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
  // 녹화를 시도한 제보에만 전환 버튼을 둔다. 사진 제보에는 고를 것이 없다.
  const hasRecording = recording.status !== null || recording.url !== null
  const recordingHint = getRecordingHint(recording.status, recording.url)
  const isVideoTab = mediaTab === 'video' && recording.url !== null

  return (
    <section className="page-stack report-detail-page">
      <nav className="breadcrumb" aria-label="제보 상세 경로">
        <Link to={ROUTE_PATHS.REPORTS}>제보 목록</Link><span aria-hidden="true">/</span>
        <span>제보 상세</span><span aria-hidden="true">/</span>
        <strong aria-current="page">제보 아이디 #{report.id}</strong>
      </nav>

      {/* 세 열은 각자 내용 높이만 갖는다. 행 높이를 맞추면 가장 긴 카드(대개 처리
          이력)에 끌려가 이미지 카드 아래에 큰 빈 공간이 생긴다. */}
      <div className="report-detail-layout">
        <DetailCard
          className="report-detail-layout__image"
          title="제보 이미지"
          icon={<ReportImageIcon />}
          action={hasRecording ? (
            <div className="report-media-tabs" role="group" aria-label="보기 방식 선택">
              <button
                type="button"
                className={mediaTab === 'image' ? 'is-active' : ''}
                aria-pressed={mediaTab === 'image'}
                onClick={() => setMediaTab('image')}
              >
                이미지
              </button>
              {/* 녹화가 아직 준비되지 않았거나 실패했으면 누를 것이 없다. 이유는 아래에 적는다. */}
              <button
                type="button"
                className={isVideoTab ? 'is-active' : ''}
                aria-pressed={isVideoTab}
                disabled={!recording.url}
                title={recordingHint ?? undefined}
                onClick={() => { setVideoError(''); setMediaTab('video') }}
              >
                원본 영상
              </button>
            </div>
          ) : undefined}
        >
          {isVideoTab ? (
            <div className="report-image-frame">
              <div className="report-image-stage">
                {/* presigned URL 이라 만료될 수 있다. 재생에 실패하면 이미지로 되돌리고 알린다. */}
                <video
                  className="report-video"
                  src={recording.url ?? undefined}
                  controls
                  playsInline
                  preload="metadata"
                  onError={() => {
                    setMediaTab('image')
                    setVideoError('원본 영상을 재생하지 못했습니다. 잠시 후 다시 시도해주세요.')
                  }}
                />
              </div>
            </div>
          ) : currentImage ? (
            /* 이미지 칸은 높이를 고정하고 사진은 비율을 지킨 채 그 안에 맞춘다.
               아래에 이동 버튼·번호·썸네일을 두어 사진 위를 가리지 않는다. */
            <div className="report-image-frame">
              <div className="report-image-stage">
                <button
                  type="button"
                  className="report-image-open"
                  aria-label={`${currentImageAlt} 크게 보기`}
                  onClick={() => setIsImageOpen(true)}
                >
                  <img className="report-image" src={currentImage.url} alt={currentImageAlt} />
                </button>
              </div>
              {previewImages.length > 1 && (
                <>
                  <div className="report-image-nav">
                    <button
                      type="button"
                      className="report-image-action"
                      aria-label="이전 이미지 보기"
                      onClick={() => moveImage(-1)}
                    >
                      <ChevronIcon direction="prev" />
                    </button>
                    <span className="report-image-counter" aria-live="polite">
                      {currentIndex + 1} / {previewImages.length}
                    </span>
                    <button
                      type="button"
                      className="report-image-action"
                      aria-label="다음 이미지 보기"
                      onClick={() => moveImage(1)}
                    >
                      <ChevronIcon direction="next" />
                    </button>
                  </div>
                  {/* 썸네일도 회색 프레임 안에 둔다. 카드 흰 배경에 두면 이미지 영역과
                      분리돼 보인다. */}
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
                </>
              )}
            </div>
          ) : (
            <div className="report-image-placeholder" role="img" aria-label={`제보 #${report.id} 이미지 없음`}>
              <span>{report.isLive ? '라이브 제보입니다.' : '제보 이미지가 없습니다.'}</span>
            </div>
          )}
          {/* 원본 영상을 왜 볼 수 없는지, 또는 왜 재생이 끊겼는지 알린다. */}
          {videoError
            ? <p className="report-media-hint" role="alert">{videoError}</p>
            : recordingHint && <p className="report-media-hint">{recordingHint}</p>}
        </DetailCard>

        {/* 가운데 열은 두 카드를 쌓는다. 기본 정보만으로는 왼쪽 이미지 높이만큼 아래가
            비므로, 척도까지 보여주는 위험도 카드를 그 자리에 둔다. */}
        <div className="report-detail-layout__mid">
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

          {/* 제보 내용은 dl 의 오른쪽 정렬 값으로 두면 긴 문장이 읽기 어려워, 같은 카드
              안에서 라벨을 위에 두고 아래로 흘린다. 카드를 따로 두면 그만큼 왼쪽 이미지
              칸이 늘어나 빈 회색 공간이 생겼다. */}
          <div className="report-content-block">
            <span className="report-content-block__label">제보 내용</span>
            <p className="report-content break-text">{summary || '제보 내용이 없습니다.'}</p>
          </div>
        </DetailCard>

        <DetailCard className="report-detail-layout__risk" title="위험도 지표" icon={<ReportRiskIcon />}>
          <RiskLevelIndicator level={riskLevel} score={riskScore} />
        </DetailCard>
        </div>

        {/* 오른쪽 열은 조치 버튼을 맨 위에 두고 그 아래에 이력을 쌓는다. 관제사가
            이력을 훑어보고 곧바로 위 버튼으로 손이 가는 순서다. */}
        <div className="report-detail-layout__side">
        <section className="admin-card report-detail-actions" aria-label="제보 처리">
          <ReportActionPanel
            reportId={numericReportId}
            assignmentStatus={myAssignmentStatus}
            isConfirmed={report.status === 'confirmed'}
            layout="row"
            onActionComplete={(action) => {
              // 승인은 종결이 아니다 — 담당자는 화면에 남아 계속 모니터링한다.
              if (action === 'confirm') setRefreshKey((key) => key + 1)
              else navigate(ROUTE_PATHS.REPORTS, { replace: true })
            }}
          />
        </section>

        <DetailCard className="report-detail-side__handling" title="처리 이력" icon={<ReportHistoryIcon />}>
          {/* 지금 누가 들고 있는지가 이 카드의 첫 정보다. 스크롤되는 이력과 달리 항상
              보여야 하므로 스크롤 영역 밖에 둔다. 값이 짧아 한 줄에 나란히 들어간다. */}
          <dl className="report-assignee">
            <div><dt>담당 기관</dt><dd>{report.controlCenterName || '-'}</dd></div>
            <div><dt>담당자</dt><dd>{assignedOperatorName || '-'}</dd></div>
          </dl>

          {/* 이력만 이 칸 안에서 스크롤한다. 카드 전체가 스크롤되면 위 담당 정보까지
              밀려 올라가고, 상한이 없으면 이력이 쌓일수록 페이지가 계속 길어진다. */}
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
