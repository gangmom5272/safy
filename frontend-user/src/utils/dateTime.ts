type DateValue = string | number | Date

function parseDate(value: DateValue) {
  const date = value instanceof Date ? value : new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

export function formatRelativeTime(value: DateValue, now: Date = new Date()) {
  const date = parseDate(value)
  if (!date) return typeof value === 'string' ? value : '시간 정보 없음'

  const elapsedMilliseconds = Math.max(0, now.getTime() - date.getTime())
  const elapsedMinutes = Math.floor(elapsedMilliseconds / 60_000)
  if (elapsedMinutes < 1) return '방금 전'
  if (elapsedMinutes < 60) return `${elapsedMinutes}분 전`

  const elapsedHours = Math.floor(elapsedMinutes / 60)
  if (elapsedHours < 24) return `${elapsedHours}시간 전`

  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const targetDay = new Date(date.getFullYear(), date.getMonth(), date.getDate())
  const elapsedDays = Math.floor((today.getTime() - targetDay.getTime()) / 86_400_000)
  if (elapsedDays === 1) return '어제'
  if (elapsedDays < 30) return `${elapsedDays}일 전`

  const elapsedMonths = Math.max(1, Math.floor(elapsedDays / 30))
  if (elapsedMonths < 12) return `${elapsedMonths}개월 전`
  return `${Math.floor(elapsedMonths / 12)}년 전`
}

export function formatDateTime(value: DateValue) {
  const date = parseDate(value)
  if (!date) return typeof value === 'string' && value ? value : '시간 정보 없음'

  const period = date.getHours() < 12 ? '오전' : '오후'
  const hour = date.getHours() % 12 || 12
  const minute = String(date.getMinutes()).padStart(2, '0')
  return `${date.getFullYear()}년 ${date.getMonth() + 1}월 ${date.getDate()}일 ${period} ${hour}시 ${minute}분`
}

export function formatDateGroupLabel(value: DateValue, now: Date = new Date()) {
  const date = parseDate(value)
  if (!date) return typeof value === 'string' ? value : '날짜 정보 없음'

  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const targetDay = new Date(date.getFullYear(), date.getMonth(), date.getDate())
  const elapsedDays = Math.floor((today.getTime() - targetDay.getTime()) / 86_400_000)
  if (elapsedDays === 0) return '오늘'
  if (elapsedDays === 1) return '어제'
  return `${date.getFullYear()}년 ${date.getMonth() + 1}월 ${date.getDate()}일`
}
