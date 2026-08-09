import { RouterProvider } from 'react-router/dom'
import { router } from './app/router'
import { AuthProvider } from './auth/AuthContext'

function App() {
  return (
    <AuthProvider>
      <RouterProvider router={router} />
    </AuthProvider>
  )
}

export default App
