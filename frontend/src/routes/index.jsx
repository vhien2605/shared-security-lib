import { Navigate } from 'react-router-dom'
import CallbackHandler from '../components/CallbackHandler'
import ProtectedRoute from '../components/ProtectedRoute'
import HeaderSidebarLayout from '../layouts/HeaderSidebarLayout'
import LoginPage from '../pages/LoginPage'
import SettingsManagementPage from '../pages/SettingsManagementPage'
import ServicesPage from '../pages/ServicesPage'
import EndpointsPage from '../pages/EndpointsPage'
import SecurityPoliciesPage from '../pages/SecurityPoliciesPage'
import AuditLogsPage from '../pages/AuditLogsPage'

const routes = [
  {
    path: '/',
    element: <LoginPage />,
  },
  {
    path: '/callback',
    element: <CallbackHandler />,
  },
  {
    path: '/settings-management',
    element: (
      <ProtectedRoute>
        <HeaderSidebarLayout>
          <SettingsManagementPage />
        </HeaderSidebarLayout>
      </ProtectedRoute>
    ),
  },
  {
    path: '/services',
    element: (
      <ProtectedRoute>
        <HeaderSidebarLayout>
          <ServicesPage />
        </HeaderSidebarLayout>
      </ProtectedRoute>
    ),
  },
  {
    path: '/endpoints',
    element: (
      <ProtectedRoute>
        <HeaderSidebarLayout>
          <EndpointsPage />
        </HeaderSidebarLayout>
      </ProtectedRoute>
    ),
  },
  {
    path: '/security-policies',
    element: (
      <ProtectedRoute>
        <HeaderSidebarLayout>
          <SecurityPoliciesPage />
        </HeaderSidebarLayout>
      </ProtectedRoute>
    ),
  },
  {
    path: '/audit-logs',
    element: (
      <ProtectedRoute>
        <HeaderSidebarLayout>
          <AuditLogsPage />
        </HeaderSidebarLayout>
      </ProtectedRoute>
    ),
  },
  {
    path: '/dashboard',
    element: <Navigate to="/settings-management" replace />,
  },
  {
    path: '*',
    element: <Navigate to="/" replace />,
  },
]

export default routes
