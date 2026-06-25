import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { fetchNotifications, markNotificationRead, openNotificationSocket } from '../services/notifications'

function mergeNewest(current, incoming) {
  if (!incoming?.id) return current
  if (current.some((item) => item.id === incoming.id)) return current
  return [incoming, ...current].slice(0, 50)
}

export function useNotifications(isAuthenticated) {
  const [items, setItems] = useState([])
  const [error, setError] = useState(null)
  const socketRef = useRef(null)

  useEffect(() => {
    let cancelled = false
    if (!isAuthenticated) {
      return undefined
    }

    fetchNotifications(50)
      .then((notifications) => {
        if (!cancelled) setItems(notifications)
      })
      .catch((err) => {
        if (!cancelled) setError(err)
      })

    openNotificationSocket(
      (notification) => setItems((current) => mergeNewest(current, notification)),
      (err) => setError(err),
    ).then((socket) => {
      if (cancelled && socket) socket.close()
      else socketRef.current = socket
    })

    return () => {
      cancelled = true
      if (socketRef.current) {
        socketRef.current.close()
        socketRef.current = null
      }
    }
  }, [isAuthenticated])

  const visibleItems = useMemo(() => (isAuthenticated ? items : []), [isAuthenticated, items])
  const unreadCount = useMemo(() => visibleItems.filter((item) => !item.read).length, [visibleItems])

  const markRead = useCallback(async (id) => {
    const updated = await markNotificationRead(id)
    setItems((current) => current.map((item) => (item.id === id ? { ...item, ...updated, read: true } : item)))
  }, [])

  return { notifications: visibleItems, unreadCount, markRead, error }
}
