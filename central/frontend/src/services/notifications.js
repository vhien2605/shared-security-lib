import keycloak from '../keycloak'
import { WS_BASE_URL } from '../constants'
import { apiGet, apiPatch } from './api'

export async function fetchNotifications(limit = 10) {
  const data = await apiGet(`/api/notifications?limit=${encodeURIComponent(limit)}`)
  return Array.isArray(data) ? data : []
}

export async function markNotificationRead(id) {
  if (!id) throw new Error('Notification id is required')
  return apiPatch(`/api/notifications/${encodeURIComponent(id)}/read`, {})
}

export async function openNotificationSocket(onMessage, onError) {
  if (!keycloak.authenticated) return null
  try {
    await keycloak.updateToken(10)
  } catch (error) {
    onError?.(error)
    return null
  }
  if (!keycloak.token) return null
  const socket = new WebSocket(`${WS_BASE_URL}/ws/notifications?token=${encodeURIComponent(keycloak.token)}`)
  socket.onmessage = (event) => {
    try {
      onMessage(JSON.parse(event.data))
    } catch (error) {
      onError?.(error)
    }
  }
  socket.onerror = (event) => onError?.(event)
  return socket
}
