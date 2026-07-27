import type { ReactNode } from 'react'
import { QueryProvider } from './QueryProvider'
import { AuthBootstrap } from '../../pages/auth/AuthBootstrap'

type AppProvidersProps = {
  children: ReactNode
}

export function AppProviders({ children }: AppProvidersProps) {
  return <QueryProvider><AuthBootstrap>{children}</AuthBootstrap></QueryProvider>
}
