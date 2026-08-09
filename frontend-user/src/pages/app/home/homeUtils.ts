import type { AreaLocation } from './types'

const SITUATIONS_PATH = '/app/situations'

interface SituationUrlOptions {
  location?: AreaLocation | null
  sort?: string
}

export function buildSituationUrl({ location, sort }: SituationUrlOptions = {}) {
  const params = new URLSearchParams()

  if (sort !== undefined) params.set('sort', sort)
  // 지원 행정구역이 아니면 areaId·areaName 이 null 로 온다.
  if (location?.areaId != null) params.set('areaId', String(location.areaId))
  if (location?.areaName != null) params.set('areaName', location.areaName)
  if (location?.latitude !== undefined) params.set('latitude', String(location.latitude))
  if (location?.longitude !== undefined) params.set('longitude', String(location.longitude))

  const query = params.toString()
  return query ? `${SITUATIONS_PATH}?${query}` : SITUATIONS_PATH
}
