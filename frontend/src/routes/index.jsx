import { Navigate } from 'react-router-dom'
import CallbackHandler from '../components/CallbackHandler'
import ProtectedRoute from '../components/ProtectedRoute'
import HeaderSidebarLayout from '../layouts/HeaderSidebarLayout'
import LoginPage from '../pages/login/LoginPage'
import SettingsManagementPage from '../pages/setting/SettingsManagementPage'
import SettingDetailServicePage from '../pages/setting/SettingDetailService/SettingDetailServicePage'
import ServicesPage from '../pages/ServicesPage'
import EndpointsPage from '../pages/EndpointsPage'
import SecurityPoliciesPage from '../pages/SecurityPoliciesPage'
import AuditLogsPage from '../pages/AuditLogsPage'
import ClientManagementPage from '../pages/client/ClientManagementPage'
import ClientDetailPage from '../pages/client/ClientDetailPage'
import PermissionControlPage from '../pages/access-control/PermissionControlPage'

const routes = [
  {
    path: '/',
    element: <LoginPage />,
  },
  {
    path: '/settings-management/services/:serviceId',
    element: (
      <ProtectedRoute>
        <HeaderSidebarLayout>
          <SettingDetailServicePage />
        </HeaderSidebarLayout>
      </ProtectedRoute>
    ),
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
    path: '/clients',
    element: (
      <ProtectedRoute>
        <HeaderSidebarLayout>
          <ClientManagementPage />
        </HeaderSidebarLayout>
      </ProtectedRoute>
    ),
  },
  {
    path: '/clients/:clientId',
    element: (
      <ProtectedRoute>
        <HeaderSidebarLayout>
          <ClientDetailPage />
        </HeaderSidebarLayout>
      </ProtectedRoute>
    ),
  },
  {
    path: '/permissions',
    element: (
      <ProtectedRoute>
        <HeaderSidebarLayout>
          <PermissionControlPage />
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
