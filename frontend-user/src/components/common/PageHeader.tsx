import './pageHeader.css'

type PageHeaderProps = {
  title: string
  onBack?: () => void
  backLabel?: string
  action?: React.ReactNode
  className?: string
  titleId?: string
}

export function PageHeader({ title, onBack, backLabel = '뒤로가기', action, className = '', titleId }: PageHeaderProps) {
  return (
    <header className={`page-header${className ? ` ${className}` : ''}`}>
      {onBack ? <button type="button" className="page-header__back" aria-label={backLabel} onClick={onBack}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m15 5-7 7 7 7"/><path d="M8 12h12"/></svg></button> : null}
      <h1 className="page-header__title" id={titleId}>{title}</h1>
      {action ? <div className="page-header__action">{action}</div> : null}
    </header>
  )
}
