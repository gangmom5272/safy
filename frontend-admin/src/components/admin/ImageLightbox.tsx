import { useEffect, type MouseEvent } from 'react'

interface ImageLightboxProps {
  src: string
  alt: string
  onClose: () => void
}

export function ImageLightbox({ src, alt, onClose }: ImageLightboxProps) {
  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }

    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [onClose])

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
      </div>
    </div>
  )
}
