import type { ReactNode } from 'react'
import type { StatusTone } from '../../utils/adminStatus'

interface StatusBadgeProps {
  tone: StatusTone
  children: ReactNode
  className?: string
}

export function StatusBadge({ tone, children, className = '' }: StatusBadgeProps) {
  return (
    <span className={`status-badge status-badge--${tone} ${className}`.trim()}>
      {children}
    </span>
  )
}
