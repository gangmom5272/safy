import { useState } from 'react'
import { formatRelativeTime } from '../../../utils/dateTime'
import { ErrorState, LoadingSkeleton } from './SituationStates'
import { congestionLevelLabels, congestionLevelTones, type CongestionInfo, type AreaStatus } from './types'

/**
 * 서울시 실시간 도시데이터의 추정 인원 범위. 만 단위가 넘으면 자릿수를 세지 않아도
 * 규모를 알 수 있게 '4.2만~5.0만'으로 줄인다.
 */
function formatPopulationRange(congestion: CongestionInfo) {
  const { populationMin, populationMax } = congestion

  if (populationMin === undefined && populationMax === undefined) {
    return '정보 없음'
  }

  const format = (value: number) =>
    value >= 10000 ? `${(value / 10000).toFixed(1)}만` : value.toLocaleString('ko-KR')

  if (populationMin === undefined || populationMax === undefined) {
    return `${format(populationMin ?? populationMax!)}명`
  }

  return populationMin === populationMax
    ? `${format(populationMin)}명`
    : `${format(populationMin)}~${format(populationMax)}명`
}

function formatRadius(meters: number) {
  return meters >= 1000 ? `${(meters / 1000).toFixed(meters % 1000 === 0 ? 0 : 1)}km` : `${meters}m`
}

/**
 * 혼잡도·인구수가 어느 장소를 근거로 삼았는지 밝힌다.
 *
 * <p>`nearest` 는 검색한 지점을 덮는 관측 지점이 없어 가까운 곳으로 대체한 경우다. 정확히
 * 그 안에서 관측된 값과 문구가 같으면 사용자가 정확도를 구분할 수 없어 표현을 나눈다.
 */
function congestionSourceText(status: AreaStatus) {
  if (!status.congestion) {
    return '이 지점 근처에는 서울시 실시간 도시데이터 관측 지점이 없습니다.'
  }

  const { placeName, observedAt } = status.congestion
  const basis = status.congestionMatchType === 'nearest'
    ? `가까운 ${placeName} 기준`
    : `${placeName} 기준`

  return [
    basis,
    '서울시 실시간 도시데이터',
    observedAt ? `${formatRelativeTime(observedAt)} 관측` : null,
  ]
    .filter(Boolean)
    .join(' · ')
}

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
        {status ? <>
          <div className="status-grid">
            <StatusItem
              label="실시간 인구"
              value={status.congestion ? formatPopulationRange(status.congestion) : '정보 없음'}
            />
            <StatusItem
              label="혼잡 단계"
              value={status.congestion ? congestionLevelLabels[status.congestion.level] : '정보 없음'}
              tone={status.congestion ? congestionLevelTones[status.congestion.level] : undefined}
            />
            <StatusItem label="최근 제보" value={`${status.recentReportCount}건`} />
          </div>
          {/* 어느 장소 기준인지, 언제 관측된 값인지 밝혀야 한다. 자치구 전체 인구가 아니라
              한 장소의 값이고, 위의 '마지막 갱신'은 조회 시각이라 관측 시각과 다르다. */}
          <p className="area-status-source">{congestionSourceText(status)}</p>
          {/* 좌표 기준 조회는 혼잡도(한 장소)와 최근 제보(반경)의 범위가 달라 따로 밝힌다. */}
          {status.reportRadiusMeters ? (
            <p className="area-status-source">
              최근 제보는 이 지점 반경 {formatRadius(status.reportRadiusMeters)} 기준입니다.
            </p>
          ) : null}
        </> : null}
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
