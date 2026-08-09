import { createContext, useContext } from 'react'

export const NOTIFICATIONS_UPDATED_EVENT = 'safy:notifications-updated'

export const NotificationUnreadContext = createContext(0)

export function useNotificationUnreadCount() {
  return useContext(NotificationUnreadContext)
}

export function notifyUnreadCountChanged() {
  window.dispatchEvent(new Event(NOTIFICATIONS_UPDATED_EVENT))
}
