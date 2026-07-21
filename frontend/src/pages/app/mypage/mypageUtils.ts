import type { FavoriteLocation } from './mypageTypes'
export const APP_VERSION = '1.0.0'
export function buildFavoriteSituationUrl(location: FavoriteLocation) { const params = new URLSearchParams({ areaId: String(location.areaId), areaName: location.placeName }); if (location.latitude != null) params.set('latitude', String(location.latitude)); if (location.longitude != null) params.set('longitude', String(location.longitude)); return `/app/situations?${params.toString()}` }
