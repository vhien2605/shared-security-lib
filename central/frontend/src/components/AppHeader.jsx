import { memo, useEffect, useMemo, useRef, useState } from 'react'
import { useAuth } from '../hooks/useAuth'
import { useNotifications } from '../hooks/useNotifications'

function AppHeader() {
  const { user, logout, isAuthenticated } = useAuth()
  const [isMenuOpen, setIsMenuOpen] = useState(false)
  const [isNotificationOpen, setIsNotificationOpen] = useState(false)
  const [notificationPage, setNotificationPage] = useState(0)
  const [copiedNotificationId, setCopiedNotificationId] = useState('')
  const userMenuRef = useRef(null)
  const notificationRef = useRef(null)
  const { notifications, unreadCount, markRead } = useNotifications(isAuthenticated)
  const name = user?.name || user?.username || 'admin'
  const avatarLabel = name.charAt(0).toUpperCase()
  const role = user?.role || 'Quản trị viên'
  const notificationPageSize = 5
  const notificationTotalPages = Math.max(1, Math.ceil(notifications.length / notificationPageSize))
  const safeNotificationPage = Math.min(notificationPage, notificationTotalPages - 1)
  const pagedNotifications = useMemo(() => {
    const start = safeNotificationPage * notificationPageSize
    return notifications.slice(start, start + notificationPageSize)
  }, [safeNotificationPage, notifications])

  useEffect(() => {
    const onClickOutside = (event) => {
      if (userMenuRef.current && !userMenuRef.current.contains(event.target)) {
        setIsMenuOpen(false)
      }
      if (notificationRef.current && !notificationRef.current.contains(event.target)) {
        setIsNotificationOpen(false)
      }
    }

    const onEsc = (event) => {
      if (event.key === 'Escape') {
        setIsMenuOpen(false)
        setIsNotificationOpen(false)
      }
    }

    document.addEventListener('mousedown', onClickOutside)
    document.addEventListener('keydown', onEsc)

    return () => {
      document.removeEventListener('mousedown', onClickOutside)
      document.removeEventListener('keydown', onEsc)
    }
  }, [])

  const selectNotification = (notification) => {
    if (!notification.read) markRead(notification.id)
  }

  const onNotificationKeyDown = (event, notification) => {
    if (event.key !== 'Enter' && event.key !== ' ') return
    event.preventDefault()
    selectNotification(notification)
  }

  const copyTraceId = async (event, notification) => {
    event.stopPropagation()
    if (!notification.traceId) return
    try {
      await navigator.clipboard?.writeText(notification.traceId)
      setCopiedNotificationId(notification.id)
      window.setTimeout(() => setCopiedNotificationId((current) => (current === notification.id ? '' : current)), 1500)
    } catch {
      setCopiedNotificationId('')
    }
  }

  return (
    <header className="app-header">
      <div className="app-header__right">
        <div className="app-header__notify-wrap" ref={notificationRef}>
          <button
            className="app-header__icon-btn app-header__icon-btn--notify"
            type="button"
            aria-label="Thông báo"
            aria-haspopup="menu"
            aria-expanded={isNotificationOpen}
            onClick={() => setIsNotificationOpen((prev) => !prev)}
          >
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path
                d="M12 4a5 5 0 00-5 5v2.8c0 .9-.3 1.8-.9 2.5L5 16h14l-1.1-1.7a4.3 4.3 0 01-.9-2.5V9a5 5 0 00-5-5z"
                stroke="currentColor"
                strokeWidth="1.6"
                fill="none"
                strokeLinejoin="round"
              />
              <path d="M9.5 18a2.5 2.5 0 005 0" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
            </svg>
            {unreadCount > 0 ? <span className="app-header__badge">{unreadCount > 9 ? '9+' : unreadCount}</span> : null}
          </button>
          {isNotificationOpen ? (
            <div className="app-header__notifications" role="menu" aria-label="Danh sách thông báo">
              <div className="app-header__notifications-title">
                <span>Thông báo</span>
                {notifications.length > 0 ? <small>{unreadCount} chưa đọc</small> : null}
              </div>
              {notifications.length === 0 ? (
                <div className="app-header__notifications-empty">Chưa có thông báo mới</div>
              ) : (
                <>
                  <div className="app-header__notification-list">
                    {pagedNotifications.map((notification) => (
                      <div
                        key={notification.id}
                        className={`app-header__notification-item${notification.read ? ' app-header__notification-item--read' : ' app-header__notification-item--unread'}`}
                        role="menuitem"
                        tabIndex={0}
                        onClick={() => selectNotification(notification)}
                        onKeyDown={(event) => onNotificationKeyDown(event, notification)}
                      >
                        <div className="app-header__notification-meta">
                          <span className={`app-header__notification-severity app-header__notification-severity--${String(notification.severity || '').toLowerCase()}`}>
                            {notification.severity || 'INFO'}
                          </span>
                          <button
                            type="button"
                            className="app-header__notification-copy"
                            disabled={!notification.traceId}
                            onClick={(event) => copyTraceId(event, notification)}
                            aria-label={notification.traceId ? `Copy traceId ${notification.traceId}` : 'Thông báo không có traceId'}
                            title={notification.traceId ? 'Copy traceId' : 'Không có traceId'}
                          >
                            {copiedNotificationId === notification.id ? 'Đã copy' : 'Copy traceId'}
                          </button>
                        </div>
                        <strong>{notification.title}</strong>
                        <small>{notification.content}</small>
                        {notification.traceId ? <em>traceId: {notification.traceId}</em> : null}
                      </div>
                    ))}
                  </div>
                  <div className="app-header__notifications-pagination" aria-label="Phân trang thông báo">
                    <button type="button" disabled={safeNotificationPage === 0} onClick={() => setNotificationPage((page) => Math.max(0, page - 1))}>
                      Trước
                    </button>
                    <span>{safeNotificationPage + 1}/{notificationTotalPages}</span>
                    <button type="button" disabled={safeNotificationPage + 1 >= notificationTotalPages} onClick={() => setNotificationPage((page) => Math.min(notificationTotalPages - 1, page + 1))}>
                      Sau
                    </button>
                  </div>
                </>
              )}
            </div>
          ) : null}
        </div>
        <div className="app-header__user-wrap" ref={userMenuRef}>
          <button
            className="app-header__user-trigger"
            type="button"
            onClick={() => setIsMenuOpen((prev) => !prev)}
            aria-haspopup="menu"
            aria-expanded={isMenuOpen}
            aria-label="Mở menu tài khoản"
          >
            <div className="app-header__avatar" aria-hidden="true">
              {avatarLabel}
            </div>
            <div className="app-header__user">
              <strong>{name}</strong>
              <span>{role}</span>
            </div>
            <svg className="app-header__chevron" viewBox="0 0 24 24" aria-hidden="true">
              <path d="M19 9l-7 7-7-7" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </button>

          {isMenuOpen ? (
            <div className="app-header__menu" role="menu" aria-label="Menu tài khoản">
              <button className="app-header__menu-item" type="button" role="menuitem" onClick={logout}>
                Đăng xuất
              </button>
            </div>
          ) : null}
        </div>
      </div>
    </header>
  )
}

export default memo(AppHeader)
