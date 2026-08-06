import type { ReportStatus } from '../../types/admin'

/**
 * 상태 전이 이력 한 건. 백엔드 ReportStatusHistoryResponse 와 같은 모양이되 상태는
 * 화면에서 쓰는 ReportStatus 로 이미 바꾼 값이다.
 *
 * 관제 담당자가 쓸 수 있는 이력 API 는 아직 없다. `/reports/{id}/status-history` 는
 * 제보자 본인(findByIdAndReporterId)만 조회할 수 있고, 응답도 시민에게 보여줄 상태로
 * 접어서 내려주기 때문에 재배정·이관이 사라진다. 그래서 지금은 상세 응답에서 단계를
 * 계산하고, 담당자용 이력 API 가 생기면 toReportProgressSteps 에 history 만 넘기면 된다.
 */
export interface ReportStatusChange {
  status: ReportStatus
  changedAt: string
}

export interface ReportProgressStep {
  status: ReportStatus
  /** 화면에 쓰는 단계 이름. 기본은 reportStatusLabels 지만 이관처럼 같은 상태에서
   *  갈라지는 담당자 조치는 여기서만 다르게 적는다(ReportStatus 에 상태를 새로 만들지 않는다). */
  title: string
  /** 그 단계에 들어간 시각. 이력이 없어 알 수 없으면 null 로 두고 화면에서 생략한다. */
  changedAt: string | null
  description: string
  isCurrent: boolean
}

/**
 * 처리 단계 순서. 접수 → 진행 → 종결 세 자리이며, 진행 중인 두 상태(확인 중·처리 중)와
 * 종결 상태 네 개(처리 완료·반려·취소·재배정)는 서로 배타적이라 같은 자리를 쓴다.
 * 값은 types/admin 의 ReportStatus 를 그대로 쓰고 새 상태명을 만들지 않는다.
 */
const PROGRESS_ORDER: Record<ReportStatus, number> = {
  received: 0,
  checking: 1,
  in_progress: 1,
  completed: 2,
  rejected: 2,
  cancelled: 2,
  reassigned: 2,
}

/** 단계 이름. reportStatusLabels 와 같은 값이지만 이력에서는 조치처럼 읽히게 적는다. */
const PROGRESS_TITLES: Record<ReportStatus, string> = {
  received: '접수 대기 중',
  checking: '확인 중',
  in_progress: '처리 중',
  completed: '처리 완료',
  rejected: '반려됨',
  cancelled: '취소됨',
  reassigned: '재배정됨',
}

const PROGRESS_DESCRIPTIONS: Record<ReportStatus, string> = {
  received: '제보가 접수되어 담당자 배정을 기다렸습니다.',
  checking: '담당자에게 배정되어 확인을 시작했습니다.',
  in_progress: '담당자가 제보를 확인하고 처리 중입니다.',
  completed: '담당자가 제보 처리를 완료했습니다.',
  rejected: '담당자가 제보를 반려했습니다.',
  cancelled: '제보가 취소되었습니다.',
  reassigned: '담당자가 같은 관제센터의 다른 담당자에게 재배정했습니다.',
}

/** 이관은 별도 ReportStatus 가 없어 재배정 단계의 이름과 설명만 바꿔 쓴다. */
const TRANSFER_STEP = {
  title: '이관됨',
  description: '담당자가 제보를 다른 관제센터로 이관했습니다.',
}

/**
 * 담당자가 제보를 다른 관제센터로 이관하면 내 배정만 취소되고 제보는 계속 살아 있어서
 * 배정 결과가 재배정과 똑같이 나온다. 두 조치를 가르는 단서는 "제보의 담당 관제센터가
 * 아직 내 센터인가" 하나뿐이라, 센터 이름이 둘 다 확인될 때만 이관으로 본다.
 */
function isTransferredOut(myControlCenterName?: string | null, assignedControlCenterName?: string | null) {
  if (!myControlCenterName?.trim() || !assignedControlCenterName?.trim()) return false
  return myControlCenterName.trim() !== assignedControlCenterName.trim()
}

interface ReportProgressInput {
  /** 제보 자체의 현재 상태. */
  status: ReportStatus
  /** 접수 시각. 접수 단계는 이력이 없어도 이 값으로 시각을 채운다. */
  reportedAt: string
  /** 조회한 담당자 본인의 배정 결과. 아직 진행 중이면 null. */
  outcome: ReportStatus | null
  /** 로그인한 담당자의 관제센터 이름. 재배정과 이관을 가르는 데만 쓴다. */
  myControlCenterName?: string | null
  /** 제보에 현재 배정된 관제센터 이름. */
  assignedControlCenterName?: string | null
  /** 담당자용 이력 API 가 연결되면 넘긴다. 없으면 단계 시각이 비어 있을 뿐 단계는 나온다. */
  history?: ReportStatusChange[]
}

export function toReportProgressSteps({
  status,
  reportedAt,
  outcome,
  myControlCenterName,
  assignedControlCenterName,
  history = [],
}: ReportProgressInput): ReportProgressStep[] {
  const changedAtOf = (target: ReportStatus) =>
    history.filter((item) => item.status === target).at(-1)?.changedAt ?? null

  // 종결 단계는 '내 배정 결과'가 있으면 그 값이 더 정확하다. 재배정·이관으로 넘긴 건은
  // 제보가 다른 담당자에게 넘어가 계속 살아 있어서, 제보 상태만 보면 내가 넘겼다는
  // 사실이 사라진다. 상세 화면의 '처리 결과'와 같은 기준이다.
  const terminal = outcome ?? (PROGRESS_ORDER[status] === 2 ? status : null)
  // 종결까지 갔다면 진행 단계는 반드시 거쳐온 것이므로 '처리 중'으로 적는다.
  const progressStatus = PROGRESS_ORDER[status] === 1 ? status : 'in_progress'
  const reachedProgress = PROGRESS_ORDER[status] >= 1 || terminal !== null
  const transferred = terminal === 'reassigned'
    && isTransferredOut(myControlCenterName, assignedControlCenterName)

  const reached: Pick<ReportProgressStep, 'status' | 'changedAt'>[] = [
    { status: 'received', changedAt: changedAtOf('received') ?? reportedAt },
  ]
  if (reachedProgress) reached.push({ status: progressStatus, changedAt: changedAtOf(progressStatus) })
  if (terminal) reached.push({ status: terminal, changedAt: changedAtOf(terminal) })

  // 도달하지 않은 단계는 넣지 않으므로 마지막 단계가 곧 현재 단계다.
  return reached.map((step, index) => {
    const isTransferStep = transferred && step.status === 'reassigned'
    return {
      ...step,
      title: isTransferStep ? TRANSFER_STEP.title : PROGRESS_TITLES[step.status],
      description: isTransferStep ? TRANSFER_STEP.description : PROGRESS_DESCRIPTIONS[step.status],
      isCurrent: index === reached.length - 1,
    }
  })
}

/** 현재 단계가 반려·취소로 끝났는지. 타임라인에서 빨간색으로 강조할지 판단한다. */
export function isFailedProgressStep(step: ReportProgressStep) {
  return step.isCurrent && (step.status === 'rejected' || step.status === 'cancelled')
}
