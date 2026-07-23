export interface AdminSession {
  isAuthenticated: boolean
  email: string
}

export interface LoginCredentials {
  email: string
  password: string
}

export interface AuthContextValue {
  isAuthenticated: boolean
  adminEmail: string | null
  login: (credentials: LoginCredentials) => boolean
  logout: () => void
}
