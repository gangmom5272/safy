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
