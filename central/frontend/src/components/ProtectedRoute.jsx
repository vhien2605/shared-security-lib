import { useEffect } from 'react'
import { useAuth } from '../hooks/useAuth'
import { useNavigate } from 'react-router-dom'

export default function ProtectedRoute({ children }) {
  const { isAuthenticated, initializing } = useAuth()
  const navigate = useNavigate()

  useEffect(() => {
    if (!initializing && !isAuthenticated) {
      navigate('/', { replace: true })
    }
  }, [isAuthenticated, initializing, navigate])

  if (initializing) return null
  if (!isAuthenticated) return null

  return children
}
