import { Outlet } from 'react-router-dom'
import AppHeader from '../components/AppHeader'
import AppSidebar from '../components/AppSidebar'
import './HeaderSidebarLayout.css'

export default function HeaderSidebarLayout({ children }) {
  return (
    <div className="app-shell">
      <AppSidebar />
      <div className="app-shell__main">
        <AppHeader />
        <main className="app-shell__content">{children || <Outlet />}</main>
      </div>
    </div>
  )
}
