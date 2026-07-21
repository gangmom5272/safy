import type { AppNotification } from './notificationsTypes'

export function getNotificationTargetUrl(notification: AppNotification) {
  if (notification.targetType === 'REPORT') {
    const id = notification.reportId ?? notification.targetId
    return id != null ? `/app/reports/${id}` : null
  }

  if (notification.targetType === 'AREA') {
    const id = notification.areaId ?? notification.targetId
    return id != null ? `/app/situations/areas/${id}` : null
  }

  return null
}
