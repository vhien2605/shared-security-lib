import { useState, useEffect, useCallback, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { AuthContext } from './authContext'
import keycloak from '../keycloak'
import { REDIRECT_URI, POST_LOGOUT_REDIRECT_URI } from '../constants'

export function AuthProvider({ children }) {
  const navigate = useNavigate()
  const [token, setToken] = useState(() => localStorage.getItem('access_token'))
  const [authError, setAuthError] = useState(null)
  const [initializing, setInitializing] = useState(true)
  const initStarted = useRef(false)

  useEffect(() => {
    if (initStarted.current) return
    initStarted.current = true

    keycloak
      .init({
        onLoad: 'check-sso',
        checkLoginIframe: false,
        redirectUri: REDIRECT_URI,
      })
      .then((authenticated) => {
        if (authenticated) {
          localStorage.setItem('access_token', keycloak.token)
          if (keycloak.refreshToken) {
            localStorage.setItem('refresh_token', keycloak.refreshToken)
          }
          if (keycloak.idToken) {
            localStorage.setItem('id_token', keycloak.idToken)
          }
          setToken(keycloak.token)

          if (window.location.pathname === '/callback') {
            navigate('/dashboard', { replace: true })
          }
        } else {
          if (window.location.pathname === '/callback') {
            navigate('/', { replace: true })
          }
        }
        setInitializing(false)
      })
      .catch((err) => {
        console.error('Keycloak init failed:', err)
        setAuthError(err.message || 'Keycloak initialization failed')
        setInitializing(false)
      })

    keycloak.onTokenExpired = () => {
      keycloak
        .updateToken(30)
        .then((refreshed) => {
          if (refreshed) {
            localStorage.setItem('access_token', keycloak.token)
            setToken(keycloak.token)
          }
        })
        .catch(() => {
          localStorage.removeItem('access_token')
          localStorage.removeItem('refresh_token')
          localStorage.removeItem('id_token')
          setToken(null)
          keycloak.logout({ redirectUri: POST_LOGOUT_REDIRECT_URI })
        })
    }
  }, [])

  const login = useCallback(() => {
    keycloak.login({ redirectUri: REDIRECT_URI })
  }, [])

  const logout = useCallback(() => {
    localStorage.removeItem('access_token')
    localStorage.removeItem('refresh_token')
    localStorage.removeItem('id_token')
    setToken(null)
    setAuthError(null)
    keycloak.logout({ redirectUri: POST_LOGOUT_REDIRECT_URI })
  }, [])

  const clearAuthError = useCallback(() => setAuthError(null), [])

  return (
    <AuthContext.Provider
      value={{ token, isAuthenticated: !!token, authError, clearAuthError, login, logout, initializing }}
    >
      {children}
    </AuthContext.Provider>
  )
}
