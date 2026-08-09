import apiClient from '../../api/client'
import type { ApiResponse } from '../../api/types'

// 시민 앱이 보는 공개 피드와 같은 엔드포인트다(GET /feed/reports).
// PublicReportFeedController 가 CITIZEN 과 함께 OPERATOR·SUPER_ADMIN 을 허용하므로
// 담당자 토큰으로 그대로 호출된다 — 관리자 전용 API 를 새로 만들 필요가 없다.
//
// 노출 조건은 전부 서버가 정한다(PublicReportQueryService):
//   publicVisibility = PUBLISHED  담당자가 완료 처리해 공개된 제보
//   reportedAt > now - 30분        제보 시각 기준 롤링 창
// 관리자 대시보드 지도가 쓰는 집합(배정 상태 ASSIGNED·IN_PROGRESS)과는 배타적이다.
// 완료 처리하는 순간 대시보드에서 빠지고 이 피드로 넘어오므로 핀이 중복되지 않는다.

/** 서버 PublicReportQueryService.RECENT_WINDOW 와 같은 값. 남은 공개 시간 표시에만 쓴다. */
const PUBLIC_WINDOW_MS = 30 * 60 * 1000

/** 서버 상한. 시민 앱은 20건만 받지만 관리자는 공개 집합 전체를 봐야 한다. */
const MAX_PAGE_SIZE = 100

export interface PublicFeedReport {
  reportId: number
  reportType: 'IMAGE' | 'LIVE'
  category: string
  subcategory: string
  description: string
  latitude: number | null
  longitude: number | null
  address: string | null
  areaId: number | null
  areaCode: string | null
  areaName: string | null
  reportedAt: string
  blurredImageUrl: string | null
  blurredImageExpiresAt: string | null
}

interface PublicFeedCursorResponse {
  content: PublicFeedReport[]
  nextCursor: string | null
  hasNext: boolean
}

export async function getPublicFeedReports(signal?: AbortSignal) {
  const response = await apiClient.get<ApiResponse<PublicFeedCursorResponse>>('/feed/reports', {
    params: { size: MAX_PAGE_SIZE },
    signal,
  })

  const { content, hasNext } = response.data.data
  // 30분 안에 100건이 넘게 공개되면 커서를 이어 받아야 한다. 조용히 잘라 놓고
  // '공개 집합 전체'라고 보여주면 안 되므로 최소한 로그로 남긴다.
  if (hasNext && import.meta.env?.DEV) {
    console.warn('공개 피드가 한 페이지를 넘었습니다. 일부 제보가 지도에서 빠집니다.', content.length)
  }
  return content
}

/**
 * 남은 공개 시간. 서버가 reportedAt 기준으로 창을 자르므로 완료 처리 시각과 무관하게
 * 제보 시각부터 카운트된다. 0 이하면 이미 시민 지도에서 사라진 제보다.
 */
export function remainingPublicMs(reportedAt: string, now: number) {
  const reported = new Date(reportedAt).getTime()
  if (Number.isNaN(reported)) return null
  return reported + PUBLIC_WINDOW_MS - now
}

/** 남은 공개 시간 라벨. 지난 건은 다음 갱신에서 사라지므로 상태를 그대로 알린다. */
export function formatRemainingPublic(remainingMs: number | null) {
  if (remainingMs === null) return '정보 없음'
  if (remainingMs <= 0) return '공개 종료'

  const minutes = Math.floor(remainingMs / 60_000)
  return minutes < 1 ? '1분 미만' : `${minutes}분`
}
