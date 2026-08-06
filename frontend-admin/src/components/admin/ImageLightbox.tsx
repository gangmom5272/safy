import { useEffect, type MouseEvent } from 'react'

interface ImageLightboxProps {
  src: string
  alt: string
  onClose: () => void
  /** 여러 장일 때만 넘긴다. 없으면 좌우 버튼과 장수 표시를 그리지 않는다. */
  onPrev?: () => void
  onNext?: () => void
  /** 예: '2 / 5'. */
  counter?: string
}

function ChevronIcon({ direction }: { direction: 'prev' | 'next' }) {
  return (
    <svg className="image-lightbox__nav-icon" viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false">
      <path d={direction === 'prev' ? 'M15 5l-7 7 7 7' : 'M9 5l7 7-7 7'} />
    </svg>
  )
}

export function ImageLightbox({ src, alt, onClose, onPrev, onNext, counter }: ImageLightboxProps) {
  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
      // 확대 상태에서는 좌우 키로도 넘긴다. 한 장뿐이면 핸들러가 없어 아무 일도 없다.
      if (event.key === 'ArrowLeft') onPrev?.()
      if (event.key === 'ArrowRight') onNext?.()
    }

    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [onClose, onNext, onPrev])

  // 배경을 눌렀을 때만 닫는다. 이미지 위 클릭은 무시한다.
  const handleBackdropClick = (event: MouseEvent<HTMLDivElement>) => {
    if (event.target === event.currentTarget) onClose()
  }

  return (
    <div className="dialog-backdrop image-lightbox" role="presentation" onMouseDown={handleBackdropClick}>
      <div className="image-lightbox__frame" role="dialog" aria-modal="true" aria-label={alt}>
        <button
          type="button"
          className="image-lightbox__close"
          onClick={onClose}
          aria-label="이미지 닫기"
        >
          ×
        </button>
        <img className="image-lightbox__image" src={src} alt={alt} />
        {onPrev && (
          <button
            type="button"
            className="image-lightbox__nav image-lightbox__nav--prev"
            onClick={onPrev}
            aria-label="이전 이미지 보기"
          >
            <ChevronIcon direction="prev" />
          </button>
        )}
        {onNext && (
          <button
            type="button"
            className="image-lightbox__nav image-lightbox__nav--next"
            onClick={onNext}
            aria-label="다음 이미지 보기"
          >
            <ChevronIcon direction="next" />
          </button>
        )}
        {counter && <span className="image-lightbox__counter">{counter}</span>}
      </div>
    </div>
  )
}
