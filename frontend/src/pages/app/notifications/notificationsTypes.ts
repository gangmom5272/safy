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
  targetId?: number | null
  areaId?: number | null
  reportId?: number | null
  areaName?: string | null
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
