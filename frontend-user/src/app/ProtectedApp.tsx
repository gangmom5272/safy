import { Navigate } from 'react-router'
import { AppLayout } from '../layouts/AppLayout'
import { useAuthStore } from '../stores/authStore'

export function ProtectedApp() {
  const status = useAuthStore((state) => state.status)
  if (status === 'initializing') return <p role="status">세션을 확인하고 있습니다...</p>
  return status === 'authenticated' ? <AppLayout /> : <Navigate to="/login" replace />
}
