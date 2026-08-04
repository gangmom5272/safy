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
  return SUBCATEGORY_LABELS[value] ?? value
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
