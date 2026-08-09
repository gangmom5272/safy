import { useCallback, useEffect, useRef, useState } from 'react'
import type { AdminReport, WebRTCConnection } from '../../types/admin'
import { subscribeToRealtimeSignals } from '../../api/realtime'
import { toTimestamp } from '../../utils/adminDate'
import { getLiveSessions } from '../monitoring/monitoringApi'
import { getDashboardReports, markReportChecked } from '../reports/reportApi'

/** 긴급 알림도 같은 목록에서 계산하므로 서버가 허용하는 활성 배정 최대 개수를 조회한다. */
const DASHBOARD_REPORT_SIZE = 100

/** 최근 배정 순. 문자열 비교 대신 시각으로 비교하고, 시각이 없는 제보는 뒤로 보낸다. */
function byRecentAssignment(left: AdminReport, right: AdminReport): number {
  const leftTime = toTimestamp(left.assignedAt ?? left.reportedAt)
  const rightTime = toTimestamp(right.assignedAt ?? right.reportedAt)
  if (leftTime === null && rightTime === null) return 0
  if (leftTime === null) return 1
  if (rightTime === null) return -1
  return rightTime - leftTime
}

/**
 * 홈 화면의 단일 제보 상태.
 * 지도와 최근 배정 제보 테이블이 이 목록 하나만 보므로 두 영역의 확인 상태가 어긋나지 않는다.
 * 실시간 갱신은 기존 SSE 구독(subscribeToRealtimeSignals)을 그대로 재사용한다.
 */
export function useDashboardReports(enabled = true) {
  const [reports, setReports] = useState<AdminReport[]>([])
  const [connections, setConnections] = useState<WebRTCConnection[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState('')

  // 전용 확인 API 가 없어, 서버 재조회 결과가 다시 NEW 로 되돌리지 않도록 확인한 제보를 기억한다.
  const locallyCheckedRef = useRef(new Set<number>())
  // SSE 이벤트가 연속으로 와 조회가 겹쳐도 가장 최근 요청만 화면 상태를 갱신한다.
  const loadRequestIdRef = useRef(0)

  /** reportId 기준으로 중복을 걷어내고, 로컬 확인 결과를 덮어씌운 뒤 최신순으로 정렬한다. */
  const mergeReports = useCallback((loaded: AdminReport[]): AdminReport[] => {
    const unique = new Map<number, AdminReport>()

    for (const report of loaded) {
      if (unique.has(report.id)) continue
      unique.set(
        report.id,
        locallyCheckedRef.current.has(report.id)
          ? { ...report, verificationStatus: 'CHECKED' }
          : report,
      )
    }

    return [...unique.values()].sort(byRecentAssignment)
  }, [])

  const loadDashboard = useCallback(async () => {
    const requestId = ++loadRequestIdRef.current

    try {
      const [loadedReports, loadedConnections] = await Promise.all([
        getDashboardReports(0, DASHBOARD_REPORT_SIZE),
        getLiveSessions(),
      ])
      if (requestId !== loadRequestIdRef.current) return
      setReports(mergeReports(loadedReports))
      setConnections(loadedConnections)
      setLoadError('')
    } catch (error) {
      if (requestId !== loadRequestIdRef.current) return
      setLoadError(error instanceof Error ? error.message : '대시보드 정보를 불러오지 못했습니다.')
    } finally {
      if (requestId === loadRequestIdRef.current) setIsLoading(false)
    }
  }, [mergeReports])

  useEffect(() => {
    if (!enabled) return
    const initialLoad = window.setTimeout(() => void loadDashboard(), 0)
    // 배정·라이브·종결 이벤트는 목록을 바꾸므로 재조회한다. REPORT_MESSAGE 는 목록과 무관하다.
    const unsubscribe = subscribeToRealtimeSignals((signal) => {
      if (signal.kind === 'connected' || signal.event.eventType !== 'REPORT_MESSAGE') {
        void loadDashboard()
      }
    })
    return () => {
      window.clearTimeout(initialLoad)
      unsubscribe()
      // 화면을 떠난 뒤 완료되는 요청이 상태를 갱신하지 못하게 한다.
      loadRequestIdRef.current += 1
    }
  }, [enabled, loadDashboard])

  /** 담당자가 제보를 열면 지도 마커와 테이블 행을 동시에 '확인'으로 바꾼다. */
  const markChecked = useCallback((reportId: number) => {
    locallyCheckedRef.current.add(reportId)
    setReports((current) => current.map((report) => (
      report.id === reportId && report.verificationStatus === 'NEW'
        ? { ...report, verificationStatus: 'CHECKED' }
        : report
    )))
    void markReportChecked(reportId)
  }, [])

  return { reports, connections, isLoading, loadError, markChecked }
}
