const KOREAN_WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'] as const

export function formatAdminDate(value?: string | null): string {
  if (!value?.trim()) {
    return '정보 없음'
  }

  const normalized = value.trim().replace(' ', 'T')
  const date = new Date(normalized)

  if (Number.isNaN(date.getTime())) {
    return '정보 없음'
  }

  const pad = (number: number) => String(number).padStart(2, '0')

  return `${date.getFullYear()}.${pad(date.getMonth() + 1)}.${pad(date.getDate())} (${KOREAN_WEEKDAYS[date.getDay()]}) ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

export function formatAdminTimestamp(value?: string | null): string | null {
  if (!value?.trim()) return null

  const date = new Date(value.trim().replace(' ', 'T'))
  if (Number.isNaN(date.getTime())) return null

  const pad = (number: number) => String(number).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}
