import { create } from 'zustand'
import type { UserResponse } from '../pages/auth/authSession'

export type AuthStatus = 'initializing' | 'authenticated' | 'unauthenticated'

type AuthState = {
  status: AuthStatus
  user: UserResponse | null
  sessionMessage: string | null
  authenticate: (user: UserResponse) => void
  clearAuthentication: (message?: string) => void
  finishInitialization: () => void
  clearSessionMessage: () => void
}

export const useAuthStore = create<AuthState>((set) => ({
  status: 'initializing',
  user: null,
  sessionMessage: null,
  authenticate: (user) => set({ status: 'authenticated', user, sessionMessage: null }),
  clearAuthentication: (message) => set({
    status: 'unauthenticated',
    user: null,
    sessionMessage: message ?? null,
  }),
  finishInitialization: () => set((state) => state.status === 'initializing'
    ? { status: 'unauthenticated' }
    : state),
  clearSessionMessage: () => set({ sessionMessage: null }),
}))
