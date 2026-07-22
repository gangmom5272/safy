export type NotificationType = 'REPORT' | 'SAFETY' | 'SYSTEM'
export type NotificationFilter = 'ALL' | NotificationType
export type NotificationTargetType = 'REPORT' | 'AREA' | 'NOTICE' | 'NONE'

export interface AppNotification {
  id: number
  type: NotificationType
  title: string
  content: string
  createdAt: string
  isRead: boolean
  isImportant: boolean
  targetType?: NotificationTargetType | null
  targetId?: number | string | null
  areaId?: number | string | null
  locationId?: number | string | null
  reportId?: number | string | null
  areaName?: string | null
  locationName?: string | null
  regionName?: string | null
}

export interface NotificationSettingsData {
  enabled: boolean
  reportEnabled: boolean
  safetyEnabled: boolean
  systemEnabled: boolean
  savedLocationEnabled: boolean
}

export interface NotificationPageResponse {
  content: AppNotification[]
  totalElements?: number
}
