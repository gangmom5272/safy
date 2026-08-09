import '../styles/common.css'

export function Card({
  children,
  className = '',
  elevated = false,
  ...props
}: React.HTMLAttributes<HTMLDivElement> & { elevated?: boolean }) {
  const classes = `card ${elevated ? 'card--elevated' : ''} ${className}`
  return <div className={classes} {...props}>{children}</div>
}

export function SectionHeader({
  label,
  title,
  description,
  action,
}: {
  label?: string
  title: string
  description?: string
  action?: React.ReactNode
}) {
  return (
    <div className="section-header">
      <div className="section-header__content">
        {label && <span className="section-header__label">{label}</span>}
        <h2 className="section-header__title">{title}</h2>
        {description && <p className="section-header__description">{description}</p>}
      </div>
      {action && <div className="section-header__action">{action}</div>}
    </div>
  )
}

export function Button({
  children,
  variant = 'primary',
  icon = false,
  className = '',
  ...props
}: React.ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: 'primary' | 'secondary' | 'tertiary' | 'ghost'
  icon?: boolean
}) {
  const classes = `button button--${variant} ${icon ? 'button--icon' : ''} ${className}`
  return (
    <button className={classes} {...props}>
      {children}
    </button>
  )
}

export function IconButton({
  children,
  variant = 'primary',
  className = '',
  ...props
}: React.ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: 'primary' | 'secondary' | 'ghost'
}) {
  const classes = `button button--icon button--icon-${variant} ${className}`
  return (
    <button className={classes} {...props}>
      {children}
    </button>
  )
}

export function FAB({
  children,
  className = '',
  ...props
}: React.ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button className={`fab ${className}`} {...props}>
      {children}
    </button>
  )
}

export function Badge({
  children,
  variant = 'primary',
  className = '',
  ...props
}: React.HTMLAttributes<HTMLDivElement> & {
  variant?:
    | 'primary'
    | 'danger'
    | 'warning'
    | 'success'
    | 'info'
    | 'very-crowded'
    | 'crowded'
    | 'normal'
    | 'relaxed'
}) {
  const classes = `badge badge--${variant} ${className}`
  return (
    <div className={classes} {...props}>
      {children}
    </div>
  )
}

export function Alert({
  children,
  variant = 'info',
  title,
  icon,
  className = '',
  ...props
}: React.HTMLAttributes<HTMLDivElement> & {
  variant?: 'info' | 'warning' | 'danger' | 'success'
  title?: string
  icon?: React.ReactNode
}) {
  const defaultIcons = {
    info: 'ℹ️',
    warning: '⚠️',
    danger: '⛔',
    success: '✓',
  }

  return (
    <div className={`alert alert--${variant} ${className}`} {...props}>
      {(icon || defaultIcons[variant]) && (
        <div className="alert__icon">{icon || defaultIcons[variant]}</div>
      )}
      <div className="alert__content">
        {title && <div className="alert__title">{title}</div>}
        {children}
      </div>
    </div>
  )
}

export function Divider({
  className = '',
  ...props
}: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={`divider divider--horizontal ${className}`} {...props} />
}

export function EmptyState({
  icon = '📭',
  title,
  description,
  action,
  className = '',
}: {
  icon?: string
  title: string
  description?: string
  action?: React.ReactNode
  className?: string
}) {
  return (
    <div className={`empty-state ${className}`}>
      <div className="empty-state__icon">{icon}</div>
      <h3 className="empty-state__title">{title}</h3>
      {description && <p className="empty-state__description">{description}</p>}
      {action && action}
    </div>
  )
}

export function Skeleton({
  variant = 'text',
  className = '',
  ...props
}: React.HTMLAttributes<HTMLDivElement> & {
  variant?: 'text' | 'avatar' | 'card'
}) {
  const classes = `skeleton ${variant !== 'text' ? `skeleton--${variant}` : 'skeleton--text'} ${className}`
  return <div className={classes} {...props} />
}

export function LoadingState({ message = '로드 중...' }: { message?: string }) {
  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 'var(--spacing-lg)',
        padding: 'var(--spacing-3xl) var(--spacing-lg)',
        textAlign: 'center',
        color: 'var(--color-text-secondary)',
      }}
    >
      <div style={{ fontSize: '32px', opacity: 0.6 }}>⏳</div>
      <p style={{ fontSize: 'var(--font-size-sm)' }}>{message}</p>
    </div>
  )
}
