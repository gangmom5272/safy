import { useState, type MouseEvent } from 'react'
import { useNavigate } from 'react-router'
import { reportTypeLabels, riskLevelLabels, type LiveReportSummary } from './types'

type LiveReportCardProps = {
  report: LiveReportSummary
}

export function LiveReportCard({ report }: LiveReportCardProps) {
  const navigate = useNavigate()
  const [imageFailed, setImageFailed] = useState(false)
  const [shareMessage, setShareMessage] = useState('')

  const detailUrl = `${window.location.origin}/app/reports/${report.id}`

  const openDetail = () => {
    navigate(`/app/reports/${report.id}`)
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
        {report.isWebRtc ? <span className="webrtc-tag">WebRTC 제보</span> : null}
        {report.isLive ? <span className="rec-badge">REC</span> : null}
      </div>

      <div className="report-card-content">
        <div className="report-card-badges">
          <span className={`risk-badge risk-${report.riskLevel}`}>
            {riskLevelLabels[report.riskLevel]}
          </span>
          {report.isLive ? <span className="live-badge">LIVE</span> : null}
        </div>

        <p className="report-category">{reportTypeLabels[report.type]}</p>
        <h3>{report.title}</h3>
        <p className="report-meta">{report.locationName}</p>
        <p className="report-time">{formatReportedAt(report.reportedAt)}</p>
        {report.description ? <p className="report-description">{report.description}</p> : null}

        <div className="report-card-actions">
          <button type="button" aria-label={`${report.title} 공유하기`} onClick={shareReport}>
            공유
          </button>
          <span aria-hidden="true">상세보기</span>
        </div>
        {shareMessage ? <p className="share-feedback">{shareMessage}</p> : null}
      </div>
    </article>
  )
}

function formatReportedAt(value: string) {
  const date = new Date(value)

  if (Number.isNaN(date.getTime())) {
    return value
  }

  return new Intl.DateTimeFormat('ko-KR', {
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}
