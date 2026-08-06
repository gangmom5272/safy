import type { ReportHandlingEvent, ReportHandlingEventType, ReportHandlingHistory } from './reportApi'

/** 타임라인 한 줄. 담당 이력 API 의 사건 하나가 그대로 한 단계가 된다. */
export interface ReportProgressStep {
  /** React key 용. 같은 종류의 조치가 여러 번 있을 수 있어 시각을 함께 쓴다. */
  key: string
  title: string
  description: string
  /** 담당자가 입력한 원본 사유. 문구를 다듬지 않고 그대로 보여준다. */
  reason: string | null
  changedAt: string | null
  isCurrent: boolean
  /** 반려·취소처럼 정상 종결이 아닌 마지막 단계인지. 타임라인에서 빨간색으로 강조한다. */
  isFailed: boolean
}

const EVENT_TITLES: Record<ReportHandlingEventType, string> = {
  AUTO_ASSIGNED: '담당자 배정',
  CLAIMED: '담당자 지정',
  VIEWED: '확인 시작',
  COMPLETED: '처리 완료',
  REJECTED: '반려됨',
  REASSIGNED: '재배정됨',
  TRANSFERRED: '이관됨',
  AUTO_TRANSFERRED: '자동 이관됨',
  ASSIGNMENT_TIMEOUT: '배정 회수됨',
  OPERATOR_OFFLINE: '배정 회수됨',
  OPERATOR_AWAY: '배정 회수됨',
  REPORTER_CANCELLED: '제보 취소됨',
}

/** 정상 종결이 아닌 끝. 마지막 단계일 때만 빨간색으로 쓴다. */
const FAILED_EVENTS = new Set<ReportHandlingEventType>(['REJECTED', 'REPORTER_CANCELLED'])

const operatorLabel = (event: ReportHandlingEvent) =>
  event.operatorName ? `${event.operatorName} 담당자가` : '담당자가'

function describeEvent(event: ReportHandlingEvent): string {
  const center = event.controlCenterName
  const destination = event.toControlCenterName

  switch (event.type) {
    case 'AUTO_ASSIGNED':
      return center
        ? `${center}의 담당자에게 자동 배정되었습니다.`
        : '담당자에게 자동 배정되었습니다.'
    case 'CLAIMED':
      return `${operatorLabel(event)} 미배정 목록에서 직접 담당했습니다.`
    case 'VIEWED':
      return `${operatorLabel(event)} 제보를 확인하기 시작했습니다.`
    case 'COMPLETED':
      return `${operatorLabel(event)} 제보 처리를 완료했습니다.`
    case 'REJECTED':
      return `${operatorLabel(event)} 제보를 반려했습니다.`
    case 'REASSIGNED':
      return `${operatorLabel(event)} 같은 관제센터의 다른 담당자에게 넘겼습니다.`
    case 'TRANSFERRED':
      return destination
        ? `${operatorLabel(event)} ${destination}로 이관했습니다.`
        : `${operatorLabel(event)} 다른 관제센터로 이관했습니다.`
    case 'AUTO_TRANSFERRED':
      return destination
        ? `근무 중인 담당자가 없어 ${destination}로 자동 이관되었습니다.`
        : '근무 중인 담당자가 없어 다른 관제센터로 자동 이관되었습니다.'
    case 'ASSIGNMENT_TIMEOUT':
      return '유예 시간 안에 확인하지 않아 배정이 회수되었습니다.'
    case 'OPERATOR_OFFLINE':
      return `${operatorLabel(event)} 오프라인으로 바뀌어 배정이 회수되었습니다.`
    case 'OPERATOR_AWAY':
      return `${operatorLabel(event)} 자리비움으로 바뀌어 배정이 회수되었습니다.`
    case 'REPORTER_CANCELLED':
      return '제보자가 제보를 취소했습니다.'
  }
}

/**
 * 담당 이력 응답을 타임라인 단계로 옮긴다.
 *
 * 접수 단계는 사건이 아니라 stages.receivedAt 에서 만든다. 그 뒤로는 서버가 준 events 를
 * 그대로 한 줄씩 쓴다. 재배정과 이관은 상태 전이가 같아 상태로는 끝내 구분되지 않으므로
 * 반드시 event.type 으로 판별한다(관제센터 이름 비교는 쓰지 않는다).
 */
export function toReportProgressSteps(history: ReportHandlingHistory): ReportProgressStep[] {
  const steps: Omit<ReportProgressStep, 'isCurrent' | 'isFailed'>[] = [
    {
      key: 'received',
      title: '접수 대기 중',
      description: '제보가 접수되어 담당자 배정을 기다렸습니다.',
      reason: null,
      changedAt: history.stages.receivedAt,
    },
    ...history.events.map((event, index) => ({
      key: `${event.type}-${event.occurredAt}-${index}`,
      title: EVENT_TITLES[event.type] ?? '처리 상태 변경',
      description: describeEvent(event),
      reason: event.reason?.trim() ? event.reason.trim() : null,
      changedAt: event.occurredAt,
    })),
  ]

  const lastIndex = steps.length - 1
  const lastEventType = history.events.at(-1)?.type
  return steps.map((step, index) => ({
    ...step,
    isCurrent: index === lastIndex,
    isFailed: index === lastIndex && lastEventType !== undefined && FAILED_EVENTS.has(lastEventType),
  }))
}
