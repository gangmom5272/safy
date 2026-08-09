type QueryFilter = Record<string, string | number | boolean | null | undefined>

export const queryKeys = {
  reports: {
    all: ['reports'] as const,
    lists: () => [...queryKeys.reports.all, 'list'] as const,
    list: (filters?: QueryFilter) => [...queryKeys.reports.lists(), filters ?? {}] as const,
    live: (filters?: QueryFilter) => [...queryKeys.reports.lists(), 'live', filters ?? {}] as const,
    details: () => [...queryKeys.reports.all, 'detail'] as const,
    detail: (reportId: string | number) => [...queryKeys.reports.details(), reportId] as const,
    analysis: (reportId: string | number) =>
      [...queryKeys.reports.detail(reportId), 'analysis'] as const,
    webrtc: (reportId: string | number) =>
      [...queryKeys.reports.detail(reportId), 'webrtc'] as const,
  },
  locations: {
    all: ['locations'] as const,
    search: (keyword: string) => [...queryKeys.locations.all, 'search', keyword] as const,
    // 자치구·장소 구분 없이 "이 검색어가 가리키는 대상"을 찾는 조회의 키다.
    detail: (key: string) => [...queryKeys.locations.all, 'detail', key] as const,
    // 우리 식별자가 없는 외부 검색 결과는 좌표가 캐시 키다.
    status: (latitude: number, longitude: number) =>
      [...queryKeys.locations.all, 'status', latitude, longitude] as const,
  },
  areas: {
    all: ['areas'] as const,
    details: () => [...queryKeys.areas.all, 'detail'] as const,
    detail: (areaId: string | number) => [...queryKeys.areas.details(), areaId] as const,
    status: (areaId: string | number) => [...queryKeys.areas.all, 'status', areaId] as const,
    map: (areaId: string | number) => [...queryKeys.areas.all, 'map', areaId] as const,
    liveReports: (areaId: string | number, filters?: QueryFilter) =>
      [...queryKeys.areas.all, areaId, 'reports', 'live', filters ?? {}] as const,
  },
  places: {
    all: ['places'] as const,
    status: (placeId: string | number) => [...queryKeys.places.all, 'status', placeId] as const,
  },
  map: {
    all: ['map'] as const,
    reports: (filters?: QueryFilter) => [...queryKeys.map.all, 'reports', filters ?? {}] as const,
    area: (areaId: string | number) => [...queryKeys.map.all, 'area', areaId] as const,
  },
  admin: {
    all: ['admin'] as const,
    statistics: (filters?: QueryFilter) => [...queryKeys.admin.all, 'statistics', filters ?? {}] as const,
    reports: (filters?: QueryFilter) => [...queryKeys.admin.all, 'reports', filters ?? {}] as const,
  },
  users: {
    all: ['users'] as const,
    me: () => [...queryKeys.users.all, 'me'] as const,
    details: () => [...queryKeys.users.all, 'detail'] as const,
    detail: (userId: string | number) => [...queryKeys.users.details(), userId] as const,
  },
} as const
