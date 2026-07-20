type QueryFilter = Record<string, string | number | boolean | null | undefined>

export const queryKeys = {
  reports: {
    all: ['reports'] as const,
    lists: () => [...queryKeys.reports.all, 'list'] as const,
    list: (filters?: QueryFilter) => [...queryKeys.reports.lists(), filters ?? {}] as const,
    details: () => [...queryKeys.reports.all, 'detail'] as const,
    detail: (reportId: string | number) => [...queryKeys.reports.details(), reportId] as const,
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
