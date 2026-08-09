import apiClient from '../../../api/client'
import { mockResponse, USE_MOCK_API } from '../../../api/mockMode'
import type { ApiResponse, PageResponse } from '../../../api/types'
import { mockNotifications, mockNotificationSettings } from './notificationsMock'
import { disableWebPush, ensureWebPushSubscribed } from './webPush'
import type { AppNotification, NotificationFilter, NotificationSettingsData } from './notificationsTypes'

export async function getNotifications(type: NotificationFilter = 'ALL', page = 0, size = 30) {
  if (USE_MOCK_API) {
    return mockResponse(type === 'ALL'
      ? mockNotifications
      : mockNotifications.filter((notification) => notification.type === type))
  }

  const params = { page, size, ...(type === 'ALL' ? {} : { type }) }
  const response = await apiClient.get<ApiResponse<PageResponse<AppNotification>>>('/notifications', { params })
  return response.data.data.content
}

export async function readNotification(notificationId: number) {
  if (USE_MOCK_API) {
    const notification = mockNotifications.find((item) => item.id === notificationId)
    if (notification) notification.isRead = true
    return mockResponse(undefined)
  }

  await apiClient.patch(`/notifications/${notificationId}/read`)
}

export async function readAllNotifications() {
  if (USE_MOCK_API) {
    mockNotifications.forEach((notification) => { notification.isRead = true })
    return mockResponse(undefined)
  }

  await apiClient.patch('/notifications/read-all')
}

export async function getNotificationSettings() {
  if (USE_MOCK_API) return mockResponse(mockNotificationSettings)

  return (await apiClient.get<ApiResponse<NotificationSettingsData>>('/users/me/notification-settings')).data.data
}

export async function updateNotificationSettings(settings: NotificationSettingsData) {
  if (USE_MOCK_API) return mockResponse(settings)

  if (settings.enabled) await ensureWebPushSubscribed()
  else await disableWebPush()

  const saved = (await apiClient.patch<ApiResponse<NotificationSettingsData>>(
    '/users/me/notification-settings',
    settings,
  )).data.data
  window.dispatchEvent(new Event('safy:notification-settings-updated'))
  return saved
}

export async function getUnreadNotificationCount() {
  if (USE_MOCK_API) return mockResponse(mockNotifications.filter((notification) => !notification.isRead).length)

  return (await apiClient.get<ApiResponse<number>>('/notifications/unread-count')).data.data
}
