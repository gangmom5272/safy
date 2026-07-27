import { Navigate } from 'react-router'
import { AppLayout } from '../layouts/AppLayout'
import { useAuthStore } from '../stores/authStore'

export function ProtectedApp() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated)
  return isAuthenticated ? <AppLayout /> : <Navigate to="/login" replace />
}
