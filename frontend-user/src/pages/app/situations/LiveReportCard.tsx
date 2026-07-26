import { useState, type MouseEvent } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { formatRelativeTime } from '../../../utils/dateTime'
import { reportTypeLabels, riskLevelLabels, type LiveReportSummary } from './types'

type LiveReportCardProps = {
  report: LiveReportSummary
}

export function LiveReportCard({ report }: LiveReportCardProps) {
  const navigate = useNavigate()
  const location = useLocation()
  const [imageFailed, setImageFailed] = useState(false)
  const [shareMessage, setShareMessage] = useState('')

  const detailPath = `/app/situations/reports/${report.id}${location.search}`
  const detailUrl = `${window.location.origin}${detailPath}`
  const displayTitle = getDisplayTitle(report)

  const openDetail = () => {
    navigate(detailPath)
  }

  const shareReport = async (event: MouseEvent<HTMLButtonElement>) => {
    event.stopPropagation()

    try {
      if (navigator.share) {
        await navigator.share({
          title: report.title,
          text: `${report.locationName} ${report.description ?? ''}`.trim(),
          url: detailUrl,
        })
      } else if (navigator.clipboard) {
        await navigator.clipboard.writeText(detailUrl)
      } else {
        window.prompt('공유 링크를 복사해 주세요.', detailUrl)
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

  return (
    <article
      className="live-report-card"
      role="button"
      tabIndex={0}
      onClick={openDetail}
      onKeyDown={(event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault()
          openDetail()
        }
      }}
    >
      <div className="report-thumbnail">
        {report.maskedThumbnailUrl && !imageFailed ? (
          <img
            src={report.maskedThumbnailUrl}
            alt={`${report.title} 마스킹 처리 대표 이미지`}
            onError={() => setImageFailed(true)}
          />
        ) : (
          <div className="thumbnail-placeholder" aria-label="마스킹 처리 대표 이미지 없음">
            <span>MASKED</span>
          </div>
        )}
        <span className="report-relative-time">{formatRelativeTime(report.reportedAt)} 제보</span>
      </div>

      <div className="report-card-content">
        <div className="report-card-badges">
          <span className={`risk-badge risk-${report.riskLevel}`}>
            {riskLevelLabels[report.riskLevel]}
          </span>
        </div>

        <p className="report-category">{reportTypeLabels[report.type]}</p>
        <h3>{displayTitle}</h3>
        <p className="report-meta"><MetaIcon name="location" />{report.locationName}</p>
        <p className="report-time"><MetaIcon name="clock" />{formatRelativeTime(report.reportedAt)}</p>

        <div className="report-card-actions">
          <button type="button" aria-label={`${report.title} 공유하기`} onClick={shareReport}>
            <ShareIcon />
          </button>
        </div>
        {shareMessage ? <p className="share-feedback">{shareMessage}</p> : null}
      </div>
    </article>
  )
}

function getDisplayTitle(report: LiveReportSummary) {
  const placeName = report.locationName.trim()
  if (placeName) return placeName

  const categoryLabel = reportTypeLabels[report.type]
  const withoutCategory = report.title.replace(categoryLabel, '').replace(/\s{2,}/g, ' ').trim()
  return withoutCategory || report.title
}

function MetaIcon({ name }: { name: 'location' | 'clock' }) {
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{name === 'location' ? <><path d="M20 10c0 5-8 11-8 11S4 15 4 10a8 8 0 1 1 16 0Z"/><circle cx="12" cy="10" r="2.5"/></> : <><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></>}</svg>
}

function ShareIcon() { return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M12 16V3M8 7l4-4 4 4"/><path d="M5 11v9h14v-9"/></svg> }
