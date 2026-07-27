import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { formatDateTime, formatRelativeTime } from '../../../utils/dateTime'
import { reportTypeLabels, riskLevelLabels, type LiveReportSummary } from './types'

type LiveReportCardProps = {
  report: LiveReportSummary
}

export function LiveReportCard({ report }: LiveReportCardProps) {
  const navigate = useNavigate()
  const location = useLocation()
  const [imageFailed, setImageFailed] = useState(false)
  const detailPath = `/app/situations/reports/${report.id}${location.search}`

  const openDetail = () => {
    navigate(detailPath)
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
        <span className={`risk-badge risk-${report.riskLevel}`}>
          {riskLevelLabels[report.riskLevel]}
        </span>
        {report.maskedThumbnailUrl && !imageFailed ? (
          <img
            src={report.maskedThumbnailUrl}
            alt={`${report.title} 마스킹 처리 제보 이미지`}
            onError={() => setImageFailed(true)}
          />
        ) : (
          <div className="thumbnail-placeholder" aria-label="제보 이미지 없음">
            <span>이미지 준비중</span>
          </div>
        )}
        <span className="report-relative-time">{formatRelativeTime(report.reportedAt)}</span>
      </div>

      <div className="report-card-content">
        <p className="report-list-meta">{reportTypeLabels[report.type]} · {report.locationName}</p>
        <h3>{report.title || report.locationName}</h3>
        <time dateTime={report.reportedAt}>{formatDateTime(report.reportedAt)}</time>
      </div>
    </article>
  )
}
