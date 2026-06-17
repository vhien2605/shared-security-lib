import { memo } from 'react'
import { NavLink, useLocation, useNavigate } from 'react-router-dom'
import { SIDEBAR_ITEMS } from '../constants/sidebarItems'

function SidebarIcon({ type }) {
  const common = {
    width: 18,
    height: 18,
    viewBox: '0 0 24 24',
    fill: 'none',
    xmlns: 'http://www.w3.org/2000/svg',
  }

  if (type === 'settings') {
    return (
      <svg {...common}>
        <path d="M14.6 3.7l.5 2a6.9 6.9 0 011.4.8l1.9-.8 1.8 3.1-1.5 1.3c.1.5.2 1 .2 1.6s-.1 1.1-.2 1.6l1.5 1.3-1.8 3.1-1.9-.8a6.9 6.9 0 01-1.4.8l-.5 2h-3.2l-.5-2a6.9 6.9 0 01-1.4-.8l-1.9.8-1.8-3.1 1.5-1.3A7.4 7.4 0 015 12c0-.6.1-1.1.2-1.6L3.7 9.1l1.8-3.1 1.9.8a6.9 6.9 0 011.4-.8l.5-2h3.2z" stroke="currentColor" strokeWidth="1.7" />
        <circle cx="12" cy="12" r="2.4" stroke="currentColor" strokeWidth="1.7" />
      </svg>
    )
  }

  if (type === 'overview') {
    return (
      <svg {...common}>
        <path d="M4.5 10.8L12 4l7.5 6.8" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
        <path d="M6.5 10.5V19h11v-8.5" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
        <path d="M9.5 16h5" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
      </svg>
    )
  }

  if (type === 'clients') {
    return (
      <svg {...common}>
        <circle cx="8" cy="9" r="2.5" stroke="currentColor" strokeWidth="1.7" />
        <circle cx="16" cy="8" r="2" stroke="currentColor" strokeWidth="1.7" />
        <path d="M3.8 17.5c.8-2.1 2.5-3.2 4.2-3.2 1.8 0 3.4 1.1 4.2 3.2" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
        <path d="M14 17.2c.5-1.5 1.8-2.3 3.1-2.3 1.4 0 2.6.9 3.1 2.3" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
      </svg>
    )
  }

  if (type === 'logs') {
    return (
      <svg {...common}>
        <rect x="6" y="4" width="12" height="16" rx="2" stroke="currentColor" strokeWidth="1.7" />
        <path d="M9 9h6M9 13h6M9 17h4" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
      </svg>
    )
  }

  if (type === 'permissions') {
    return (
      <svg {...common}>
        <path d="M12 3.5l6.5 2.4v5.4c0 4-2.6 7.5-6.5 9.2-3.9-1.7-6.5-5.2-6.5-9.2V5.9L12 3.5z" stroke="currentColor" strokeWidth="1.7" strokeLinejoin="round" />
        <path d="M9.5 12.2l1.8 1.8 3.5-4" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    )
  }

  if (type === 'warning') {
    return (
      <svg {...common}>
        <path d="M12 4l8 14H4l8-14z" stroke="currentColor" strokeWidth="1.7" strokeLinejoin="round" />
        <path d="M12 9v4" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
        <circle cx="12" cy="16.5" r=".9" fill="currentColor" />
      </svg>
    )
  }

  return (
    <svg {...common}>
      <path d="M5 18h14M7 18V9m5 9V6m5 12v-4" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
    </svg>
  )
}

export default memo(function AppSidebar() {
  const location = useLocation()
  const navigate = useNavigate()

  const navigateOnPointerDown = (event, path) => {
    if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
      return
    }

    if (location.pathname === path) {
      return
    }

    window.dispatchEvent(new Event('app:navigation-start'))
    navigate(path)
  }

  return (
    <aside className="app-sidebar">
      <div className="app-sidebar__brand">
        <div className="app-sidebar__shield" aria-hidden="true">
          <svg viewBox="0 0 24 24">
            <path d="M9 12l2 2 4-4m5.6-4A12 12 0 0112 2.9 12 12 0 013.4 6 12 12 0 003 9c0 5.6 3.8 10.3 9 11.6C17.2 19.3 21 14.6 21 9c0-1-.1-2-.4-3" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" fill="none" />
          </svg>
        </div>
        <div>
          <p className="app-sidebar__brand-title">VDT 2026</p>
          <p className="app-sidebar__brand-sub">Sentinel System</p>
        </div>
      </div>
      <p className="app-sidebar__section">HỆ THỐNG</p>
      <nav className="app-sidebar__menu" aria-label="Menu điều hướng">
        {SIDEBAR_ITEMS.map((item) => (
          <NavLink
            key={item.path}
            to={item.path}
            onPointerDown={(event) => navigateOnPointerDown(event, item.path)}
            className={({ isActive }) =>
              isActive ? 'app-sidebar__item app-sidebar__item--active' : 'app-sidebar__item'
            }
          >
            <span className="app-sidebar__icon" aria-hidden="true">
              <SidebarIcon type={item.icon} />
            </span>
            {item.label}
          </NavLink>
        ))}
      </nav>
    </aside>
  )
})
