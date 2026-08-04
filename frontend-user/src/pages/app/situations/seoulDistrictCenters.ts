import type { Coordinates } from './types'

/**
 * 서울 자치구 중심 좌표(구청 기준).
 *
 * 지역 검색(/locations/search)과 행정구역 상세 응답에는 좌표가 없다
 * (administrative_areas 테이블에 좌표 컬럼이 없다). 검색 대상이 서울 25개
 * 자치구로 고정되어 있으므로, 검색한 지역으로 지도를 옮기기 위한 중심 좌표를
 * 화면에서 보강한다. 백엔드에 좌표가 생기면 이 테이블은 지워도 된다.
 */
const SEOUL_DISTRICT_CENTERS: Record<string, Coordinates> = {
  종로구: { latitude: 37.5735, longitude: 126.979 },
  중구: { latitude: 37.5641, longitude: 126.9979 },
  용산구: { latitude: 37.5324, longitude: 126.99 },
  성동구: { latitude: 37.5634, longitude: 127.0369 },
  광진구: { latitude: 37.5385, longitude: 127.0823 },
  동대문구: { latitude: 37.5744, longitude: 127.0396 },
  중랑구: { latitude: 37.6063, longitude: 127.0925 },
  성북구: { latitude: 37.5894, longitude: 127.0167 },
  강북구: { latitude: 37.6396, longitude: 127.0257 },
  도봉구: { latitude: 37.6688, longitude: 127.0472 },
  노원구: { latitude: 37.6542, longitude: 127.0568 },
  은평구: { latitude: 37.6027, longitude: 126.9291 },
  서대문구: { latitude: 37.5791, longitude: 126.9368 },
  마포구: { latitude: 37.5663, longitude: 126.9014 },
  양천구: { latitude: 37.517, longitude: 126.8666 },
  강서구: { latitude: 37.551, longitude: 126.8495 },
  구로구: { latitude: 37.4954, longitude: 126.8875 },
  금천구: { latitude: 37.4569, longitude: 126.8955 },
  영등포구: { latitude: 37.5264, longitude: 126.8963 },
  동작구: { latitude: 37.5124, longitude: 126.9393 },
  관악구: { latitude: 37.4784, longitude: 126.9516 },
  서초구: { latitude: 37.4837, longitude: 127.0324 },
  송파구: { latitude: 37.5145, longitude: 127.106 },
  강동구: { latitude: 37.5301, longitude: 127.1238 },
  강남구: { latitude: 37.5173, longitude: 127.0473 },
  서울특별시: { latitude: 37.5665, longitude: 126.978 },
}

/** 지역 이름으로 중심 좌표를 찾는다. "서울 마포구"처럼 수식어가 붙어도 매칭한다. */
export function findDistrictCenter(name: string): Coordinates | null {
  const trimmed = name.trim()
  if (!trimmed) return null

  const exact = SEOUL_DISTRICT_CENTERS[trimmed]
  if (exact) return exact

  const partial = Object.keys(SEOUL_DISTRICT_CENTERS).find(
    (district) => trimmed.includes(district) || district.includes(trimmed),
  )
  return partial ? SEOUL_DISTRICT_CENTERS[partial] : null
}
