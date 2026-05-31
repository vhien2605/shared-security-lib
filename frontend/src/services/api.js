import keycloak from '../keycloak'
import { API_BASE_URL, REDIRECT_URI } from '../constants'

async function getValidToken() {
  if (!keycloak.authenticated) return null
  try {
    await keycloak.updateToken(5)
    return keycloak.token
  } catch {
    keycloak.login({ redirectUri: REDIRECT_URI })
    return null
  }
}

async function doFetch(path, options = {}) {
  const accessToken = await getValidToken()

  const headers = { ...options.headers }
  if (accessToken) {
    headers['Authorization'] = `Bearer ${accessToken}`
  }

  let response = await fetch(`${API_BASE_URL}${path}`, { ...options, headers })

  const contentType = response.headers.get('content-type')
  let data
  if (contentType && contentType.includes('application/json')) {
    data = await response.json()
  } else {
    data = await response.text()
  }

  if (data && typeof data === 'object' && data.status !== undefined && data.status !== 200) {
    const newToken = await getValidToken()
    if (newToken) {
      headers['Authorization'] = `Bearer ${newToken}`
      const retryResponse = await fetch(`${API_BASE_URL}${path}`, { ...options, headers })
      const ct = retryResponse.headers.get('content-type')
      if (ct && ct.includes('application/json')) {
        data = await retryResponse.json()
      } else {
        data = await retryResponse.text()
      }
    } else {
      return null
    }
  }

  return data
}

export async function apiGet(path) {
  return doFetch(path, { method: 'GET' })
}

export async function apiPost(path, body) {
  return doFetch(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

export async function apiPut(path, body) {
  return doFetch(path, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

export async function apiPatch(path, body) {
  return doFetch(path, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

export async function apiDelete(path) {
  return doFetch(path, { method: 'DELETE' })
}
