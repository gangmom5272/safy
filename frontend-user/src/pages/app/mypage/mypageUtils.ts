import type { FavoriteLocation } from './mypageTypes'

export const APP_VERSION = '1.0.0'

export function buildFavoriteSituationUrl(location: FavoriteLocation) {
  const params = new URLSearchParams({ query: location.placeName })
  return `/app/situations?${params.toString()}`
}
