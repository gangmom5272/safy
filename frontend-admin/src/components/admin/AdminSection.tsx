import type { ReactNode } from 'react'

interface AdminSectionProps {
  title: string
  children: ReactNode
  action?: ReactNode
  className?: string
  cardClassName?: string
}

export function AdminSection({
  title,
  children,
  action,
  className = '',
  cardClassName = '',
}: AdminSectionProps) {
  return (
    <section className={`admin-section ${className}`.trim()}>
      <div className="admin-section__heading">
        <h2>{title}</h2>
        {action}
      </div>
      <div className={`admin-card admin-section__content ${cardClassName}`.trim()}>{children}</div>
    </section>
  )
}
