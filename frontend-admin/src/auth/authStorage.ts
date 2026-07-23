import type { AdminSession } from '../types/auth'

export const ADMIN_SESSION_STORAGE_KEY = 'safy_admin_session'

function isAdminSession(value: unknown): value is AdminSession {
  if (!value || typeof value !== 'object') {
    return false
  }

  const candidate = value as Partial<AdminSession>

  return candidate.isAuthenticated === true && typeof candidate.email === 'string'
}

export function readAdminSession(): AdminSession | null {
  const storedSession = sessionStorage.getItem(ADMIN_SESSION_STORAGE_KEY)

  if (!storedSession) {
    return null
  }

  try {
    const parsedSession: unknown = JSON.parse(storedSession)

    if (isAdminSession(parsedSession)) {
      return parsedSession
    }
  } catch {
    sessionStorage.removeItem(ADMIN_SESSION_STORAGE_KEY)
    return null
  }

  sessionStorage.removeItem(ADMIN_SESSION_STORAGE_KEY)
  return null
}

export function saveAdminSession(session: AdminSession) {
  sessionStorage.setItem(ADMIN_SESSION_STORAGE_KEY, JSON.stringify(session))
}

export function clearAdminSession() {
  sessionStorage.removeItem(ADMIN_SESSION_STORAGE_KEY)
}
