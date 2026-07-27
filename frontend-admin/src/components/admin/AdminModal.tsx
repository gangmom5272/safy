import { useEffect, useId, type MouseEvent, type ReactNode } from 'react'

interface AdminModalProps {
  open: boolean
  title: string
  onClose: () => void
  children: ReactNode
  footer?: ReactNode
}

export function AdminModal({ open, title, onClose, children, footer }: AdminModalProps) {
  const titleId = useId()

  useEffect(() => {
    if (!open) return

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }

    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [onClose, open])

  if (!open) return null

  const handleBackdropClick = (event: MouseEvent<HTMLDivElement>) => {
    if (event.target === event.currentTarget) onClose()
  }

  return (
    <div className="dialog-backdrop" role="presentation" onMouseDown={handleBackdropClick}>
      <section className="admin-modal" role="dialog" aria-modal="true" aria-labelledby={titleId}>
        <div className="admin-modal__heading">
          <h2 id={titleId}>{title}</h2>
          <button type="button" className="icon-button" onClick={onClose} aria-label="모달 닫기">
            ×
          </button>
        </div>
        <div className="admin-modal__body">{children}</div>
        {footer && <div className="dialog-actions">{footer}</div>}
      </section>
    </div>
  )
}
