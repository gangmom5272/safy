type StateProps = {
  title?: string
  message: string
  actionLabel?: string
  onAction?: () => void
}

export function EmptyState({ message }: StateProps) {
  return <p className="situation-state empty">{message}</p>
}

export function ErrorState({ title = '오류가 발생했습니다.', message, actionLabel, onAction }: StateProps) {
  return (
    <div className="situation-state error" role="alert">
      <strong>{title}</strong>
      <p>{message}</p>
      {actionLabel && onAction ? (
        <button type="button" onClick={onAction}>
          {actionLabel}
        </button>
      ) : null}
    </div>
  )
}

export function LoadingSkeleton() {
  return (
    <div className="report-skeleton-list" aria-label="실시간 제보 목록 로딩 중">
      {[0, 1, 2].map((item) => (
        <div className="report-skeleton" key={item}>
          <div />
          <span />
          <span />
        </div>
      ))}
    </div>
  )
}
