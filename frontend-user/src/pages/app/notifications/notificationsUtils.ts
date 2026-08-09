import type { AppNotification } from './notificationsTypes'

export function getNotificationTargetUrl(notification: AppNotification) {
  if (notification.targetType === 'AREA_REPORT_REQUEST') {
    const id = notification.areaReportRequestId ?? notification.targetId
    return id != null ? `/app/report?requestId=${encodeURIComponent(String(id))}` : '/app/report'
  }

  if (notification.type === 'REPORT') {
    const id = notification.reportId ?? notification.targetId
    return id != null ? `/app/mypage/reports/${encodeURIComponent(String(id))}` : null
  }

  if (notification.type === 'SAFETY') {
    const areaId =
      notification.areaId ??
      notification.locationId ??
      (notification.targetType === 'AREA' ? notification.targetId : null)
    const areaName =
      notification.areaName?.trim() ??
      notification.locationName?.trim() ??
      notification.regionName?.trim() ??
      ''
    const params = new URLSearchParams()

    if (areaName) params.set('query', areaName)
    if (areaId != null && String(areaId).length > 0) params.set('areaId', String(areaId))

    const search = params.toString()
    return search ? `/app/situations?${search}` : '/app/situations'
  }

  return null
}
