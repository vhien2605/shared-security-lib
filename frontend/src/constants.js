export const KEYCLOAK_BASE = 'http://localhost:8000'
export const REALM = 'security-lib-realm'
export const CLIENT_ID = 'security-lib-client'
export const REDIRECT_URI = 'http://localhost:5173/callback'
export const POST_LOGOUT_REDIRECT_URI = 'http://localhost:5173/'
export const API_BASE_URL = 'http://localhost:8080'

function normalizeHttpBaseUrl(value, fallback, envName) {
  const rawValue = typeof value === 'string' ? value.trim() : ''
  if (!rawValue) return fallback

  try {
    const url = new URL(rawValue)
    if (url.protocol !== 'http:' && url.protocol !== 'https:') {
      throw new Error(`${envName} must use http or https protocol`)
    }

    return url.toString().replace(/\/+$/, '')
  } catch (error) {
    console.error(`Invalid ${envName}; falling back to ${fallback}.`, error)
    return fallback
  }
}

export const KIBANA_BASE_URL = normalizeHttpBaseUrl(
  import.meta.env.VITE_KIBANA_BASE_URL,
  'http://localhost:5601',
  'VITE_KIBANA_BASE_URL',
)
