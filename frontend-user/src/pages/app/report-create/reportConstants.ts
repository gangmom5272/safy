import type { ReportCategory, ReportMethod } from './reportTypes'

export const DEFAULT_REPORT_METHOD: ReportMethod = 'live'

export const DEFAULT_REPORT_CATEGORY: ReportCategory = 'crowd'

export const REPORT_METHOD_LABELS: Record<ReportMethod, string> = {
  live: '실시간 영상 제보',
  photo: '사진 제보',
}

export const REPORT_CATEGORY_LABELS: Record<ReportCategory, string> = {
  crowd: '인파 밀집',
  accident: '사고·위험',
  weather: '침수·기상',
  facility: '시설물·도로',
  etc: '기타',
}

export const REPORT_CATEGORIES: ReportCategory[] = [
  'crowd',
  'accident',
  'weather',
  'facility',
  'etc',
]

export const MAX_PHOTO_SIZE_BYTES = 10 * 1024 * 1024
