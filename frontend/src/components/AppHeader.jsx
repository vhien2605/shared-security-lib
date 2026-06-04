import { memo, useEffect, useRef, useState } from 'react'
import { useAuth } from '../hooks/useAuth'

function AppHeader() {
  const { user, logout } = useAuth()
  const [isMenuOpen, setIsMenuOpen] = useState(false)
  const userMenuRef = useRef(null)
  const name = user?.name || user?.username || 'admin'
  const avatarLabel = name.charAt(0).toUpperCase()
  const role = user?.role || 'Quản trị viên'

  useEffect(() => {
    const onClickOutside = (event) => {
      if (userMenuRef.current && !userMenuRef.current.contains(event.target)) {
        setIsMenuOpen(false)
      }
    }

    const onEsc = (event) => {
      if (event.key === 'Escape') {
        setIsMenuOpen(false)
      }
    }

    document.addEventListener('mousedown', onClickOutside)
    document.addEventListener('keydown', onEsc)

    return () => {
      document.removeEventListener('mousedown', onClickOutside)
      document.removeEventListener('keydown', onEsc)
    }
  }, [])

  return (
    <header className="app-header">
      <div className="app-header__search" role="search">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path
            d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
            stroke="currentColor"
            strokeWidth="2"
            fill="none"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
        <input type="search" placeholder="Tìm kiếm nhật ký, IP, webhook..." aria-label="Tìm kiếm" />
      </div>
      <div className="app-header__right">
        <button className="app-header__icon-btn" type="button" aria-label="Thêm mới">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M12 5v14M5 12h14" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
          </svg>
        </button>
        <button className="app-header__icon-btn app-header__icon-btn--notify" type="button" aria-label="Thông báo">
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
          <span className="app-header__dot" />
        </button>
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
