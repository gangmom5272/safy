import apiClient from '../../../api/client'
import { mockResponse, USE_MOCK_API } from '../../../api/mockMode'
import { mockNotifications, mockNotificationSettings } from './notificationsMock'
import type { AppNotification, NotificationFilter, NotificationPageResponse, NotificationSettingsData } from './notificationsTypes'

export async function getNotifications(type: NotificationFilter = 'ALL', page = 0, size = 30) {
  if (USE_MOCK_API) {
    return mockResponse(type === 'ALL'
      ? mockNotifications
      : mockNotifications.filter((notification) => notification.type === type))
  }

  const params = { page, size, ...(type === 'ALL' ? {} : { type }) }
  const { data } = await apiClient.get<NotificationPageResponse | AppNotification[]>('/api/notifications', { params })
  return Array.isArray(data) ? data : data.content
}

export async function readNotification(notificationId: number) {
  if (USE_MOCK_API) return mockResponse(undefined)

  await apiClient.patch(`/api/notifications/${notificationId}/read`)
}

export async function readAllNotifications() {
  if (USE_MOCK_API) return mockResponse(undefined)

  await apiClient.patch('/api/notifications/read-all')
}

export async function getNotificationSettings() {
  if (USE_MOCK_API) return mockResponse(mockNotificationSettings)

  return (await apiClient.get<NotificationSettingsData>('/api/users/me/notification-settings')).data
}

export async function updateNotificationSettings(settings: NotificationSettingsData) {
  if (USE_MOCK_API) return mockResponse(settings)

  return (await apiClient.patch<NotificationSettingsData>('/api/users/me/notification-settings', settings)).data
}
