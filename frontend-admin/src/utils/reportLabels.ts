import type { RecordingStatus } from '../pages/reports/reportApi'

// 백엔드 enum 을 화면에 그대로 노출하지 않기 위한 한글 라벨.
// 값 목록은 backend ReportSubcategory / RiskAssessmentStatus enum 과 맞춘다.

const SUBCATEGORY_LABELS: Record<string, string> = {
  // CROWD_CONGESTION
  MOVEMENT_DIFFICULTY: '이동 어려움',
  EXIT_CONGESTION: '출구 혼잡',
  QUEUE_INCREASE: '대기줄 증가',
  CROWD_INCREASING: '인파 증가 중',
  CRUSH_RISK: '압사 위험',
  // ACCIDENT_RISK
  TRAFFIC_ACCIDENT: '교통사고',
  FIRE_SMOKE: '화재·연기',
  PERSON_INJURED: '부상자 발생',
  FALL_COLLISION_RISK: '추락·충돌 위험',
  VIOLENCE_THREAT: '폭력·위협',
  // FLOOD_WEATHER
  ROAD_FLOODING: '도로 침수',
  BUILDING_FLOODING: '건물 침수',
  HEAVY_RAIN: '폭우',
  HEAVY_SNOW_ICE: '폭설·결빙',
  STRONG_WIND: '강풍',
  // FACILITY_ROAD
  ROAD_DAMAGE: '도로 파손',
  TRAFFIC_SIGNAL_FAILURE: '신호등 고장',
  STREETLIGHT_FAILURE: '가로등 고장',
  PUBLIC_FACILITY_DAMAGE: '공공시설 파손',
  ROAD_OBSTRUCTION: '도로 장애물',
  // OTHER
  OTHER_SITUATION: '기타 상황',
}

export function getSubcategoryLabel(value: string | null | undefined) {
  if (!value) return null

  const label = SUBCATEGORY_LABELS[value]
  if (label) return label

  // 백엔드에 세부 분류가 추가돼도 영문 enum 이 화면에 그대로 나가면 안 된다.
  // 매핑이 빠졌다는 사실은 개발 중에만 로그로 남긴다.
  if (import.meta.env?.DEV) console.warn('세부 분류 한글 라벨이 없습니다.', value)
  return '기타'
}

// RiskAssessmentStatus(COMPLETED·NOT_ASSESSABLE) + 결과가 없을 때의 NOT_STARTED.
const ANALYSIS_STATUS_LABELS: Record<string, string> = {
  NOT_STARTED: '분석 전',
  COMPLETED: '분석 완료',
  NOT_ASSESSABLE: '판정 불가',
}

export function getAnalysisStatusLabel(value: string | null | undefined) {
  if (!value) return null
  return ANALYSIS_STATUS_LABELS[value] ?? value
}

// 원본 녹화 URL 이 없을 때 왜 볼 수 없는지 알려준다. COMPLETED 인데 URL 이 없는 경우는
// 파일이 아직 스토리지에 올라오지 않은 상태다. 관제 화면과 제보 상세가 같은 문구를 쓴다.
const RECORDING_HINTS: Record<RecordingStatus, string> = {
  STARTING: '원본 영상을 준비하는 중입니다.',
  ACTIVE: '원본 영상을 준비하는 중입니다.',
  COMPLETED: '원본 영상을 아직 불러올 수 없습니다.',
  FAILED: '원본 영상이 저장되지 않았습니다.',
  ABORTED: '원본 영상이 저장되지 않았습니다.',
}

/** 재생할 수 있으면 안내가 필요 없다. 볼 수 없을 때만 이유를 돌려준다. */
export function getRecordingHint(status: RecordingStatus | null, url: string | null) {
  if (url || !status) return null
  return RECORDING_HINTS[status]
}
