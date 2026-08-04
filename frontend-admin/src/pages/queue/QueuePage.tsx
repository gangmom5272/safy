import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { AdminSection } from '../../components/admin/AdminSection'
import { RiskBadge } from '../../components/admin/RiskBadge'
import { useAuth } from '../../auth/useAuth'
import { createReportDetailPath } from '../../router/paths'
import { subscribeToRealtimeSignals } from '../../api/realtime'
import { getSubcategoryLabel } from '../../utils/reportLabels'
import { claimReport, formatElapsed, getQueue, QUEUE_CATEGORY_LABELS, type QueueItem } from './queueApi'

export function QueuePage() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const isOperator = user?.role === 'OPERATOR'
  const [items, setItems] = useState<QueueItem[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [claimingId, setClaimingId] = useState<number | null>(null)
  const [claimError, setClaimError] = useState('')

  const loadQueue = useCallback(async () => {
    try {
      setItems(await getQueue())
      setLoadError('')
    } catch (error) {
      setLoadError(error instanceof Error ? error.message : '공용 큐를 불러오지 못했습니다.')
    } finally {
      setIsLoading(false)
    }
  }, [])

  useEffect(() => {
    // 담당자가 아니면 아래 권한 안내로 빠지므로 로딩 상태를 쓰지 않는다.
    if (!isOperator) return

    const initialLoad = window.setTimeout(() => void loadQueue(), 0)
    // 배정 실패·회수·이관이 모두 큐로 흘러들어오므로 실시간 신호마다 다시 읽는다.
    const unsubscribe = subscribeToRealtimeSignals((signal) => {
      if (signal.kind === 'connected' || signal.event.eventType !== 'REPORT_MESSAGE') {
        void loadQueue()
      }
    })
    return () => {
      window.clearTimeout(initialLoad)
      unsubscribe()
    }
  }, [isOperator, loadQueue])

  const claim = async (reportId: number) => {
    if (claimingId !== null) return

    setClaimingId(reportId)
    setClaimError('')
    try {
      await claimReport(reportId)
      navigate(createReportDetailPath(reportId))
    } catch (error) {
      // 409 는 경합이다. 다른 담당자가 먼저 가져갔으니 목록을 새로 읽어 사라진 걸 보여준다.
      setClaimError(error instanceof Error ? error.message : '담당하지 못했습니다.')
      setClaimingId(null)
      void loadQueue()
    }
  }

  if (!isOperator) {
    return (
      <section className="page-stack">
        <p className="admin-card empty-state" role="status">
          공용 큐는 관제센터에 소속된 담당자만 볼 수 있습니다.
        </p>
      </section>
    )
  }

  return (
    <section className="page-stack">
      {loadError && <p className="admin-card empty-state" role="alert">{loadError}</p>}
      {claimError && <p className="login-error" role="alert">{claimError}</p>}

      <AdminSection
        title="센터 공용 큐"
        action={
          <button type="button" className="text-button" onClick={() => void loadQueue()}>
            새로고침
          </button>
        }
      >
        <p className="queue-intro">
          우리 관제센터로 접수됐지만 아직 담당자가 없는 제보입니다. 자동 배정이 실패했거나
          반납·회수된 건이 쌓이는 안전망이라, <strong>평소에는 비어 있는 것이 정상</strong>입니다.
          오래 기다린 제보가 위에 옵니다.
        </p>

        {isLoading ? (
          <p className="empty-state" role="status">불러오는 중...</p>
        ) : items.length === 0 ? (
          <p className="empty-state" role="status">
            대기 중인 제보가 없습니다. 접수된 제보가 모두 담당자에게 배정됐다는 뜻입니다.
          </p>
        ) : (
          <ul className="queue-grid">
            {items.map((item) => (
              <li key={item.reportId} className="queue-card">
                <div className="queue-card__media">
                  {item.image?.thumbnailUrl ? (
                    <img src={item.image.thumbnailUrl} alt={`제보 #${item.reportId} 현장 이미지`} />
                  ) : (
                    <span className="queue-card__placeholder">
                      {item.reportType === 'LIVE' ? '라이브 제보' : '이미지 없음'}
                    </span>
                  )}
                  {item.reportType === 'LIVE' && <span className="queue-card__live">LIVE</span>}
                </div>

                <div className="queue-card__body">
                  <div className="queue-card__top">
                    <RiskBadge value={item.risk?.level} score={item.risk?.score} />
                    <span className="queue-card__elapsed">{formatElapsed(item.reportedAt)}</span>
                  </div>

                  <strong className="queue-card__address">{item.address ?? '위치 정보 없음'}</strong>
                  <span className="queue-card__category">
                    {QUEUE_CATEGORY_LABELS[item.category] ?? '기타'}
                    {item.subcategory ? ` · ${getSubcategoryLabel(item.subcategory)}` : ''}
                  </span>
                  {item.description && <p className="queue-card__desc">{item.description}</p>}
                </div>

                <button
                  type="button"
                  className="primary-button queue-card__claim"
                  disabled={claimingId !== null}
                  onClick={() => void claim(item.reportId)}
                >
                  {claimingId === item.reportId ? '담당하는 중...' : '담당하기'}
                </button>
              </li>
            ))}
          </ul>
        )}
      </AdminSection>
    </section>
  )
}
