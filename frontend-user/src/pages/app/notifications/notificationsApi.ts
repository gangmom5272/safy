import apiClient from '../../../api/client'
import type { AppNotification, NotificationFilter, NotificationPageResponse, NotificationSettingsData } from './notificationsTypes'

export async function getNotifications(type: NotificationFilter = 'ALL', page = 0, size = 30) {
  const params = { page, size, ...(type === 'ALL' ? {} : { type }) }
  const { data } = await apiClient.get<NotificationPageResponse | AppNotification[]>('/api/notifications', { params })
  return Array.isArray(data) ? data : data.content
}

export async function readNotification(notificationId: number) {
  await apiClient.patch(`/api/notifications/${notificationId}/read`)
}

export async function readAllNotifications() {
  await apiClient.patch('/api/notifications/read-all')
}

export async function getNotificationSettings() {
  return (await apiClient.get<NotificationSettingsData>('/api/users/me/notification-settings')).data
}

export async function updateNotificationSettings(settings: NotificationSettingsData) {
  return (await apiClient.patch<NotificationSettingsData>('/api/users/me/notification-settings', settings)).data
}
