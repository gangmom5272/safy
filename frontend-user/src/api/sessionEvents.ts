const SESSION_EXPIRED_EVENT = 'safy:session-expired'

export function notifySessionExpired() {
  window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT))
}

export function subscribeToSessionExpired(listener: () => void) {
  window.addEventListener(SESSION_EXPIRED_EVENT, listener)
  return () => window.removeEventListener(SESSION_EXPIRED_EVENT, listener)
}
