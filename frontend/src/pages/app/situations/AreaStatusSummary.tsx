import { useState } from 'react'
import { ErrorState, LoadingSkeleton } from './SituationStates'
import { congestionLevelLabels, riskLevelLabels, type AreaStatus } from './types'

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

  if (isLoading && !status) {
    return <LoadingSkeleton />
  }

  if (error && !status) {
    return (
      <ErrorState
        message="지역 상황 요약을 불러오지 못했습니다."
        actionLabel="새로고침"
        onAction={() => void refreshStatus()}
      />
    )
  }

  if (!status) {
    return null
  }

  const displayUpdatedAt = refreshedAt ?? status.updatedAt

  return (
    <section className="area-status-summary" aria-labelledby="area-status-title">
      <div className="section-title-row compact">
        <div>
          <h2 id="area-status-title">상황 요약</h2>
          <p>마지막 갱신 {formatDateTime(displayUpdatedAt)}</p>
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
      <div className="status-grid">
        <StatusItem label="현재 위험도" value={riskLevelLabels[status.riskLevel]} tone={status.riskLevel} />
        <StatusItem label="혼잡 단계" value={congestionLevelLabels[status.congestionLevel]} />
        <StatusItem label="최근 제보" value={`${status.recentReportCount}건`} />
        <StatusItem label="현재 날씨" value={formatWeather(status.weather)} />
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

function formatWeather(weather: AreaStatus['weather']) {
  if (!weather) {
    return '정보 없음'
  }

  return typeof weather.temperature === 'number'
    ? `${weather.condition}, ${weather.temperature}도`
    : weather.condition
}

function formatDateTime(value: string) {
  const date = new Date(value)

  if (Number.isNaN(date.getTime())) {
    return value
  }

  return new Intl.DateTimeFormat('ko-KR', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}
