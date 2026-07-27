type SafyLogoProps = {
  className?: string
}

export function SafyLogo({ className = '' }: SafyLogoProps) {
  return (
    <div className={`safy-logo ${className}`} aria-hidden="true">
      <svg viewBox="0 0 96 108" fill="none">
        <path d="M48 4C61 13 74 16 88 19v34c0 25-14 42-40 51C22 95 8 78 8 53V19C22 16 35 13 48 4Z" fill="currentColor" />
        <path d="M48 23c-8 5-15 7-25 9v22c0 4 .4 8 1.7 11.5H43c7.7 0 13.8 6.2 13.8 13.8V87C68.3 80.4 73 70 73 54V32c-9.8-2-17-4-25-9Z" fill="#fff" />
        <path d="M48 23v22c0 7.7 6.2 14 14 14h10.4C70.7 71.3 63.7 80 48 87V66.4c0-7.7-6.2-14-14-14H23V32c10-2 17-4 25-9Z" fill="#fff" fillOpacity=".95" />
      </svg>
    </div>
  )
}
