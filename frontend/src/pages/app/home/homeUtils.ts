import type { AreaLocation } from './types'

const SITUATIONS_PATH = '/app/situations'

interface SituationUrlOptions {
  location?: AreaLocation | null
  sort?: string
}

export function buildSituationUrl({ location, sort }: SituationUrlOptions = {}) {
  const params = new URLSearchParams()

  if (sort !== undefined) params.set('sort', sort)
  if (location?.areaId !== undefined) params.set('areaId', String(location.areaId))
  if (location?.areaName !== undefined) params.set('areaName', location.areaName)
  if (location?.latitude !== undefined) params.set('latitude', String(location.latitude))
  if (location?.longitude !== undefined) params.set('longitude', String(location.longitude))

  const query = params.toString()
  return query ? `${SITUATIONS_PATH}?${query}` : SITUATIONS_PATH
}
