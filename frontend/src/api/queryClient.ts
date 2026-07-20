import { QueryClient } from '@tanstack/react-query'

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // Treat fetched data as fresh briefly to reduce duplicate early refetches.
      staleTime: 60 * 1000,
      // Retry once for transient network/server failures without hiding persistent errors.
      retry: 1,
      // Keep screen state stable when users return to the browser tab during development.
      refetchOnWindowFocus: false,
    },
  },
})
