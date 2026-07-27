import { create } from 'zustand'

type AuthState = {
  isAuthenticated: boolean
  login: () => void
  logout: () => void
}

// Remove the obsolete write-only Mock flag created by the previous login flow.
sessionStorage.removeItem('safy-mock-authenticated')

export const useAuthStore = create<AuthState>((set) => ({
  isAuthenticated: false,
  login: () => set({ isAuthenticated: true }),
  logout: () => set({ isAuthenticated: false }),
}))
