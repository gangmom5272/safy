import type { AreaLocation, AreaMapData, AreaStatus, RecentReport } from './types'

export const USE_HOME_MOCK_FALLBACK = true
export const mockLocation: AreaLocation = { resolved: true, areaId: 1, areaName: '홍대입구역', address: '서울 마포구 양화로 160', latitude: 37.5572, longitude: 126.9254 }
export const mockMap: AreaMapData = {
  center: { latitude: 37.5572, longitude: 126.9254 },
  zones: [
    { id: 1, name: '홍대입구역', latitude: 37.5572, longitude: 126.9254, congestionLevel: 'VERY_CROWDED' },
    { id: 2, name: '연남동', latitude: 37.559, longitude: 126.923, congestionLevel: 'CROWDED' },
  ],
  reports: [{ id: 101, latitude: 37.5575, longitude: 126.9251, riskLevel: 'DANGER' }],
}
export const mockStatus: AreaStatus = { riskLevel: 'DANGER', congestionLevel: 'VERY_CROWDED', recentReportCount: 3, weather: { temperature: 24, condition: '약한 비' }, updatedAt: '2026-07-21T09:41:00' }
export const mockReports: RecentReport[] = [
  { id: 101, maskedThumbnailUrl: null, thumbnailUrl: null, mediaType: 'LIVE', reportType: 'CROWD', title: '홍대입구역 9번 출구 인근 인파 과밀', locationName: '홍대입구역 9번 출구 앞', createdAt: '2026-07-21T09:39:00', riskLevel: 'DANGER', isLive: true },
  { id: 102, maskedThumbnailUrl: null, thumbnailUrl: null, mediaType: 'VIDEO', reportType: 'TRAFFIC', title: '연남동 방면 차량 정체', locationName: '경의선숲길 입구', createdAt: '2026-07-21T09:28:00', riskLevel: 'WARNING', isLive: false },
]
