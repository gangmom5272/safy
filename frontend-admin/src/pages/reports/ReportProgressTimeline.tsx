import { formatAdminDate } from '../../utils/adminDate'
import type { ReportProgressStep } from './reportProgress'

interface ReportProgressTimelineProps {
  steps: ReportProgressStep[]
}

/**
 * 처리 이력 타임라인. 사용자 앱의 처리 이력과 같은 구성(단계명·설명·일시)이지만,
 * 사용자 앱 컴포넌트는 별도 프로젝트라 관리자 화면용으로 따로 둔다.
 * 지난 단계는 회색, 마지막(현재) 단계는 파란색, 반려·취소로 끝났으면 빨간색이다.
 */
export function ReportProgressTimeline({ steps }: ReportProgressTimelineProps) {
  return (
    <ol className="status-timeline" aria-label="처리 이력">
      {steps.map((step) => {
        const state = step.isFailed ? 'failed' : step.isCurrent ? 'current' : 'complete'
        return (
          <li
            key={step.key}
            className={`status-timeline__step status-timeline__step--${state}`}
            aria-current={step.isCurrent ? 'step' : undefined}
          >
            <span className="status-timeline__marker" aria-hidden="true" />
            <div className="status-timeline__content">
              <strong>{step.title}</strong>
              <p>{step.description}</p>
              {/* 담당자가 입력한 원본 사유. 설명과 구분되게 따로 둔다. */}
              {step.reason && <p className="status-timeline__reason break-text">사유 · {step.reason}</p>}
              {step.changedAt && (
                <time dateTime={step.changedAt}>{formatAdminDate(step.changedAt)}</time>
              )}
            </div>
          </li>
        )
      })}
    </ol>
  )
}
