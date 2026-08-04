import { useState } from 'react'
import { formatRelativeTime } from '../../../utils/dateTime'
import { ErrorState, LoadingSkeleton } from './SituationStates'
import { congestionLevelLabels, shortRiskLevelLabels, type AreaStatus } from './types'

type AreaStatusSummaryProps = {
  status?: AreaStatus
  isLoading: boolean
  error: unknown
  onRefresh: () => Promise<unknown> | void
}
export function AreaStatusSummary({ status, isLoading, error, onRefresh }: AreaStatusSummaryProps) {
  const [isRefreshing, setIsRefreshing] = useState(false)
  const [refreshedAt, setRefreshedAt] = useState<string | null>(null)

  const refreshStatus = async () => {
    if (isRefreshing) {
      return
    }

    setIsRefreshing(true)

    try {
      await onRefresh()
      setRefreshedAt(new Date().toISOString())
    } catch (error) {
      console.error('Area status refresh failed', error)
      setRefreshedAt(new Date().toISOString())
    } finally {
      setIsRefreshing(false)
    }
  }

  const displayUpdatedAt = refreshedAt ?? status?.updatedAt

  return (
    <section className="situation-content-section area-status-section" aria-labelledby="area-status-title">
      <div className="section-title-row compact">
        <div>
          <h2 id="area-status-title">상황 요약</h2>
          {displayUpdatedAt ? <p>마지막 갱신 {formatRelativeTime(displayUpdatedAt)}</p> : null}
        </div>
        <button
          className="refresh-icon-button"
          type="button"
          aria-label="새로고침"
          title="새로고침"
          onClick={() => void refreshStatus()}
          disabled={isLoading || isRefreshing}
        >
          <span className={isRefreshing ? 'refresh-icon spinning' : 'refresh-icon'} aria-hidden="true">
            ↻
          </span>
        </button>
      </div>
      <div className="area-status-content">
        {isLoading && !status ? <LoadingSkeleton /> : null}
        {!isLoading && error && !status ? <ErrorState message="지역 상황 요약을 불러오지 못했습니다." actionLabel="새로고침" onAction={() => void refreshStatus()} /> : null}
        {status ? <div className="status-grid">
          {/* 라벨이 '현재 위험도'이므로 값에는 단계만 남긴다('위험도 높음' → '높음'). */}
          <StatusItem label="현재 위험도" value={shortRiskLevelLabels[status.riskLevel]} tone={status.riskLevel} />
          <StatusItem label="혼잡 단계" value={congestionLevelLabels[status.congestionLevel]} />
          <StatusItem label="최근 제보" value={`${status.recentReportCount}건`} />
        </div> : null}
      </div>
    </section>
  )
}

type StatusItemProps = {
  label: string
  value: string
  tone?: string
}

function StatusItem({ label, value, tone }: StatusItemProps) {
  return (
    <div className={tone ? `status-item status-${tone}` : 'status-item'}>
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  )
}
