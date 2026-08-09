import logoUrl from '../assets/safy-logo.png'

type SafyLogoProps = {
  className?: string
}

export function SafyLogo({ className = '' }: SafyLogoProps) {
  return (
    <div className={`safy-logo ${className}`} aria-hidden="true">
      <img src={logoUrl} alt="" />
    </div>
  )
}
