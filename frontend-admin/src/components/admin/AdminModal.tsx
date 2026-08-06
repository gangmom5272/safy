import { useEffect, useId, useRef, type MouseEvent, type ReactNode } from 'react'
import { createPortal } from 'react-dom'

interface AdminModalProps {
  open: boolean
  title: string
  onClose: () => void
  children: ReactNode
  footer?: ReactNode
  /** 닫기 버튼 접근성 이름. 알림처럼 성격이 분명한 모달에서 무엇을 닫는지 알리려면 넘긴다. */
  closeLabel?: string
}

export function AdminModal({
  open,
  title,
  onClose,
  children,
  footer,
  closeLabel = '모달 닫기',
}: AdminModalProps) {
  const titleId = useId()
  const dialogRef = useRef<HTMLElement>(null)
  const previouslyFocusedRef = useRef<HTMLElement | null>(null)

  useEffect(() => {
    if (!open) return

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }

    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [onClose, open])

  // 오버레이가 화면 전체를 덮으므로 뒤쪽 문서가 함께 스크롤되면 안 된다.
  useEffect(() => {
    if (!open) return

    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.body.style.overflow = previousOverflow
    }
  }, [open])

  // 열릴 때 포커스를 모달로 옮기고, 닫힐 때 모달을 열었던 요소로 되돌린다.
  useEffect(() => {
    if (!open) return

    previouslyFocusedRef.current = document.activeElement as HTMLElement | null
    dialogRef.current?.focus()
    return () => {
      previouslyFocusedRef.current?.focus?.()
    }
  }, [open])

  if (!open) return null

  const handleBackdropClick = (event: MouseEvent<HTMLDivElement>) => {
    if (event.target === event.currentTarget) onClose()
  }

  // 헤더 등 backdrop-filter·transform 이 걸린 부모 안에서 렌더링되면 position: fixed 오버레이가
  // 그 부모 영역까지만 덮인다. body 로 포털을 내보내 항상 viewport 전체를 덮게 한다.
  return createPortal(
    <div className="dialog-backdrop" role="presentation" onMouseDown={handleBackdropClick}>
      <section
        className="admin-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        ref={dialogRef}
        tabIndex={-1}
      >
        <div className="admin-modal__heading">
          <h2 id={titleId}>{title}</h2>
          <button type="button" className="icon-button" onClick={onClose} aria-label={closeLabel}>
            ×
          </button>
        </div>
        <div className="admin-modal__body">{children}</div>
        {footer && <div className="dialog-actions">{footer}</div>}
      </section>
    </div>,
    document.body,
  )
}
