import { Navigate } from 'react-router-dom'
import CallbackHandler from '../components/CallbackHandler'
import ProtectedRoute from '../components/ProtectedRoute'
import HeaderSidebarLayout from '../layouts/HeaderSidebarLayout'
import LoginPage from '../pages/login/LoginPage'
import SettingsManagementPage from '../pages/setting/SettingsManagementPage'
import SettingDetailServicePage from '../pages/setting/SettingDetailService/SettingDetailServicePage'
import ClientManagementPage from '../pages/client/ClientManagementPage'
import ClientDetailPage from '../pages/client/ClientDetailPage'
import PermissionControlPage from '../pages/access-control/PermissionControlPage'
import OverviewPage from '../pages/overview/OverviewPage'
import SecurityLogPage from '../pages/security-log/SecurityLogPage'
import AnomalyManagementPage from '../pages/anomaly-management/AnomalyManagementPage'

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
    path: '/overview',
    element: (
      <ProtectedRoute>
        <HeaderSidebarLayout>
          <OverviewPage />
        </HeaderSidebarLayout>
      </ProtectedRoute>
    ),
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
    path: '/security-logs',
    element: (
      <ProtectedRoute>
        <HeaderSidebarLayout>
          <SecurityLogPage />
        </HeaderSidebarLayout>
      </ProtectedRoute>
    ),
  },
  {
    path: '/anomalies',
    element: (
      <ProtectedRoute>
        <HeaderSidebarLayout>
          <AnomalyManagementPage />
        </HeaderSidebarLayout>
      </ProtectedRoute>
    ),
  },
  {
    path: '/dashboard',
    element: <Navigate to="/overview" replace />,
  },
  {
    path: '*',
    element: <Navigate to="/" replace />,
  },
]

export default routes
