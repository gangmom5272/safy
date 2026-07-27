import type { ReportCategory, ReportMethod, ReportTag } from './reportTypes'

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

export const REPORT_TAG_LABELS: Record<ReportCategory, ReportTag[]> = {
  crowd: ['이동 어려움', '출구 혼잡', '대기줄 증가', '인파 증가 중', '압사 위험'],
  accident: ['교통사고', '화재·연기', '부상자 발생', '추락·충돌 위험', '폭력·위협'],
  weather: ['도로 침수', '건물 침수', '폭우', '폭설·결빙', '강풍'],
  facility: ['도로 파손', '신호등 고장', '가로등 고장', '공공시설 파손', '도로 장애물'],
  etc: ['기타 상황'],
}

export const REPORT_CONTENT_MIN_LENGTH = 10

export const REPORT_CONTENT_MAX_LENGTH = 300

export const MAX_PHOTO_SIZE_BYTES = 10 * 1024 * 1024
