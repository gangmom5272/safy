const KOREAN_WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'] as const

const SEOUL_DATE_TIME_FORMATTER = new Intl.DateTimeFormat('ko-KR', {
  timeZone: 'Asia/Seoul',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hourCycle: 'h23',
})

export function formatSeoulDateTime(value: Date): string {
  const parts = Object.fromEntries(
    SEOUL_DATE_TIME_FORMATTER
      .formatToParts(value)
      .map((part) => [part.type, part.value]),
  )

  return `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute}:${parts.second}`
}

export function formatLastUpdated(lastUpdatedAt: number | null, now: number): string {
  if (lastUpdatedAt === null) return '갱신 대기'

  const elapsedSeconds = Math.max(0, Math.floor((now - lastUpdatedAt) / 1000))

  if (elapsedSeconds < 10) return '방금 갱신'
  if (elapsedSeconds < 60) return `갱신 ${elapsedSeconds}초 전`

  const elapsedMinutes = Math.floor(elapsedSeconds / 60)
  if (elapsedMinutes < 60) return `갱신 ${elapsedMinutes}분 전`

  const elapsedHours = Math.floor(elapsedMinutes / 60)
  if (elapsedHours < 24) return `갱신 ${elapsedHours}시간 전`

  return `갱신 ${Math.floor(elapsedHours / 24)}일 전`
}

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

/** 정렬·비교용 시각. 문자열을 그대로 비교하면 형식이 섞였을 때 순서가 어긋난다. */
export function toTimestamp(value?: string | null): number | null {
  if (!value?.trim()) return null

  const time = new Date(value.trim().replace(' ', 'T')).getTime()
  return Number.isNaN(time) ? null : time
}

export function formatAdminTimestamp(value?: string | null): string | null {
  if (!value?.trim()) return null

  const date = new Date(value.trim().replace(' ', 'T'))
  if (Number.isNaN(date.getTime())) return null

  const pad = (number: number) => String(number).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}
