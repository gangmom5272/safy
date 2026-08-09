import apiClient from '../../../api/client'
import { getNotificationSettings } from './notificationsApi'

const UPDATE_INTERVAL_MS = 2 * 60 * 1000

function sendCurrentLocation() {
  if (document.visibilityState !== 'visible' || !navigator.geolocation) return

  navigator.geolocation.getCurrentPosition(
    ({ coords }) => {
      void apiClient.put('/users/me/location-presence', {
        latitude: coords.latitude,
        longitude: coords.longitude,
      }).catch(() => undefined)
    },
    () => undefined,
    { enableHighAccuracy: false, maximumAge: 60_000, timeout: 10_000 },
  )
}

export function startLocationPresence() {
  let enabled = false

  const refresh = async () => {
    try {
      const settings = await getNotificationSettings()
      enabled = settings.enabled && settings.safetyEnabled && settings.locationRequestEnabled
      if (enabled) sendCurrentLocation()
    } catch {
      enabled = false
    }
  }
  const updateWhenVisible = () => {
    if (enabled && document.visibilityState === 'visible') sendCurrentLocation()
  }
  const intervalId = window.setInterval(updateWhenVisible, UPDATE_INTERVAL_MS)

  window.addEventListener('safy:notification-settings-updated', refresh)
  document.addEventListener('visibilitychange', updateWhenVisible)
  void refresh()

  return () => {
    window.clearInterval(intervalId)
    window.removeEventListener('safy:notification-settings-updated', refresh)
    document.removeEventListener('visibilitychange', updateWhenVisible)
  }
}
