import { Navigate } from 'react-router-dom'
import CallbackHandler from '../components/CallbackHandler'
import ProtectedRoute from '../components/ProtectedRoute'
import LoginPage from '../pages/LoginPage'
import DashboardPage from '../pages/DashboardPage'

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
    path: '/dashboard',
    element: (
      <ProtectedRoute>
        <DashboardPage />
      </ProtectedRoute>
    ),
  },
  {
    path: '*',
    element: <Navigate to="/" replace />,
  },
]

export default routes
