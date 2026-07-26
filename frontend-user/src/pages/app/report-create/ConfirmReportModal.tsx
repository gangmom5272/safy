import { useEffect, useRef } from 'react'
import './report-create.css'

interface ConfirmReportModalProps {
  title: string
  description: string
  isSubmitting: boolean
  cancelLabel?: string
  confirmLabel?: string
  onCancel: () => void
  onConfirm: () => void
}

export function ConfirmReportModal({
  title,
  description,
  isSubmitting,
  cancelLabel = '아니요',
  confirmLabel = '네, 제보할게요',
  onCancel,
  onConfirm,
}: ConfirmReportModalProps) {
  const modalRef = useRef<HTMLElement | null>(null)
  const previousActiveElementRef = useRef<HTMLElement | null>(null)

  useEffect(() => {
    previousActiveElementRef.current =
      document.activeElement instanceof HTMLElement ? document.activeElement : null
    const focusableElements = modalRef.current?.querySelectorAll<HTMLElement>(
      'button:not(:disabled), [href], input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])',
    )
    focusableElements?.[0]?.focus()

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !isSubmitting) {
        onCancel()
        return
      }

      if (event.key !== 'Tab') {
        return
      }

      const currentFocusableElements = Array.from(
        modalRef.current?.querySelectorAll<HTMLElement>(
          'button:not(:disabled), [href], input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])',
        ) ?? [],
      )

      if (currentFocusableElements.length === 0) {
        return
      }

      const firstElement = currentFocusableElements[0]
      const lastElement = currentFocusableElements[currentFocusableElements.length - 1]

      if (event.shiftKey && document.activeElement === firstElement) {
        event.preventDefault()
        lastElement.focus()
      }

      if (!event.shiftKey && document.activeElement === lastElement) {
        event.preventDefault()
        firstElement.focus()
      }
    }

    window.addEventListener('keydown', handleKeyDown)

    return () => {
      window.removeEventListener('keydown', handleKeyDown)
      previousActiveElementRef.current?.focus()
    }
  }, [isSubmitting, onCancel])

  return (
    <div className="report-modal-backdrop">
      <section
        ref={modalRef}
        className="report-confirm-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="report-confirm-title"
        aria-describedby="report-confirm-description report-confirm-warning"
      >
        <h2 id="report-confirm-title">{title}</h2>
        <p id="report-confirm-description">{description}</p>
        <p id="report-confirm-warning" className="report-modal-warning">
          허위 또는 장난 제보는 서비스 이용 제한 및 제재 대상이 될 수 있습니다.
          <br />
          실제 현장 상황이 맞는지 다시 한번 확인해주세요.
        </p>
        <div className="report-modal-actions">
          <button type="button" className="report-ghost-button" disabled={isSubmitting} onClick={onCancel}>
            {cancelLabel}
          </button>
          <button type="button" className="report-primary-button" disabled={isSubmitting} onClick={onConfirm}>
            {isSubmitting ? '제출 중...' : confirmLabel}
          </button>
        </div>
      </section>
    </div>
  )
}
