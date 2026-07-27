import type { ReportCategory, ReportFormData, ReportTag } from './reportTypes'
import type { CreateReportRequest, ReportCategoryValue, ReportSubcategory } from './reportApi'

const categoryValues: Record<ReportCategory, ReportCategoryValue> = {
  crowd: 'CROWD_CONGESTION',
  accident: 'ACCIDENT_RISK',
  weather: 'FLOOD_WEATHER',
  facility: 'FACILITY_ROAD',
  etc: 'OTHER',
}

const subcategoryValues: Record<ReportCategory, Record<ReportTag, ReportSubcategory>> = {
  crowd: {
    '이동 어려움': 'MOVEMENT_DIFFICULTY',
    '출구 혼잡': 'EXIT_CONGESTION',
    '대기줄 증가': 'QUEUE_INCREASE',
    '인파 증가 중': 'CROWD_INCREASING',
    '압사 위험': 'CRUSH_RISK',
  },
  accident: {
    '교통사고': 'TRAFFIC_ACCIDENT',
    '화재·연기': 'FIRE_SMOKE',
    '부상자 발생': 'PERSON_INJURED',
    '추락·충돌 위험': 'FALL_COLLISION_RISK',
    '폭력·위협': 'VIOLENCE_THREAT',
  },
  weather: {
    '도로 침수': 'ROAD_FLOODING',
    '건물 침수': 'BUILDING_FLOODING',
    '폭우': 'HEAVY_RAIN',
    '폭설·결빙': 'HEAVY_SNOW_ICE',
    '강풍': 'STRONG_WIND',
  },
  facility: {
    '도로 파손': 'ROAD_DAMAGE',
    '신호등 고장': 'TRAFFIC_SIGNAL_FAILURE',
    '가로등 고장': 'STREETLIGHT_FAILURE',
    '공공시설 파손': 'PUBLIC_FACILITY_DAMAGE',
    '도로 장애물': 'ROAD_OBSTRUCTION',
  },
  etc: {
    '기타 상황': 'OTHER_SITUATION',
  },
}

export function getReportSubcategory(category: ReportCategory, tag: ReportTag) {
  const subcategory = subcategoryValues[category][tag]
  if (!subcategory) throw new Error('세부 상황을 선택해 주세요.')
  return subcategory
}

export function buildCreateReportRequest(formData: ReportFormData): CreateReportRequest {
  if (!formData.category || !formData.location || !formData.selectedTags[0]) {
    throw new Error('제보 필수 정보가 부족합니다.')
  }

  return {
    reportType: formData.method === 'photo' ? 'IMAGE' : 'LIVE',
    category: categoryValues[formData.category],
    subcategory: getReportSubcategory(formData.category, formData.selectedTags[0]),
    description: formData.content,
    latitude: formData.location.latitude,
    longitude: formData.location.longitude,
    address: null,
  }
}
