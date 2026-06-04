import { useState, useEffect, useCallback, useRef } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { AuthContext } from './authContext'
import keycloak from '../keycloak'
import { REDIRECT_URI, POST_LOGOUT_REDIRECT_URI } from '../constants'

const DEFAULT_REDIRECT_PATH = '/settings-management'
const LAST_PATH_STORAGE_KEY = 'lastVisitedPath'

function pickPathFromLocation(location) {
  if (!location) return null
  const { pathname, search, hash } = location
  if (!pathname) return null
  if (pathname === '/') return null
  if (pathname === '/callback') return null
  return `${pathname}${search || ''}${hash || ''}`
}

function mapKeycloakUser() {
  const claims = keycloak.idTokenParsed || keycloak.tokenParsed
  if (!claims) return null

  const realmRoles = Array.isArray(claims.realm_access?.roles) ? claims.realm_access.roles : []
  const clientRoles = claims.resource_access
    ? Object.values(claims.resource_access).flatMap((client) =>
        Array.isArray(client?.roles) ? client.roles : [],
      )
    : []
  const mergedRoles = [...new Set([...realmRoles, ...clientRoles])]
  const role = mergedRoles.find((item) => item && item !== 'offline_access' && item !== 'uma_authorization')

  return {
    name: claims.name || claims.preferred_username || 'Người dùng',
    username: claims.preferred_username || '',
    email: claims.email || '',
    role: role || 'Quản trị viên',
  }
}

export function AuthProvider({ children }) {
  const navigate = useNavigate()
  const location = useLocation()
  const [token, setToken] = useState(() => localStorage.getItem('access_token'))
  const [user, setUser] = useState(() => mapKeycloakUser())
  const [authError, setAuthError] = useState(null)
  const [initializing, setInitializing] = useState(true)
  const initStarted = useRef(false)
  const initialRedirectDone = useRef(false)

  useEffect(() => {
    const rememberedPath = pickPathFromLocation(location) || sessionStorage.getItem(LAST_PATH_STORAGE_KEY)
    if (rememberedPath) {
      sessionStorage.setItem(LAST_PATH_STORAGE_KEY, rememberedPath)
    }
  }, [location])

  useEffect(() => {
    if (initialRedirectDone.current) return
    if (initializing) return
    if (!keycloak.authenticated) return
    const targetPath = sessionStorage.getItem(LAST_PATH_STORAGE_KEY) || DEFAULT_REDIRECT_PATH
    if (window.location.pathname !== targetPath) {
      initialRedirectDone.current = true
      sessionStorage.removeItem(LAST_PATH_STORAGE_KEY)
      navigate(targetPath, { replace: true })
    } else {
      initialRedirectDone.current = true
      sessionStorage.removeItem(LAST_PATH_STORAGE_KEY)
    }
  }, [initializing, navigate])

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
          setUser(mapKeycloakUser())
        } else {
          setUser(null)
          if (window.location.pathname === '/callback') {
            navigate('/', { replace: true })
          }
        }
        setInitializing(false)
      })
      .catch((err) => {
        console.error('Keycloak init failed:', err)
        setAuthError(err?.message || 'Không thể khởi tạo xác thực Keycloak.')
        setInitializing(false)
      })

    keycloak.onTokenExpired = () => {
      keycloak
        .updateToken(30)
        .then((refreshed) => {
          if (refreshed) {
            localStorage.setItem('access_token', keycloak.token)
            setToken(keycloak.token)
            setUser(mapKeycloakUser())
          }
        })
        .catch((error) => {
          console.error('Keycloak token refresh failed:', error)
          localStorage.removeItem('access_token')
          localStorage.removeItem('refresh_token')
          localStorage.removeItem('id_token')
          setToken(null)
          setUser(null)
          keycloak.logout({ redirectUri: POST_LOGOUT_REDIRECT_URI })
        })
    }
  }, [])

  const clearAuthError = useCallback(() => setAuthError(null), [])

  const login = useCallback(() => {
    clearAuthError()

    return keycloak.login({ redirectUri: REDIRECT_URI }).catch((error) => {
      console.error('Keycloak login redirect failed:', error)
      setAuthError('Không thể chuyển đến cổng đăng nhập Keycloak. Vui lòng thử lại.')
      throw error
    })
  }, [clearAuthError])

  const logout = useCallback(() => {
    localStorage.removeItem('access_token')
    localStorage.removeItem('refresh_token')
    localStorage.removeItem('id_token')
    setToken(null)
    setUser(null)
    setAuthError(null)
    keycloak.logout({ redirectUri: POST_LOGOUT_REDIRECT_URI })
  }, [])

  return (
    <AuthContext.Provider
      value={{ token, user, isAuthenticated: !!token, authError, clearAuthError, login, logout, initializing }}
    >
      {children}
    </AuthContext.Provider>
  )
}
