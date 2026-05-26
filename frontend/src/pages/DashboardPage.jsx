import { useState, useEffect } from 'react'
import { useAuth } from '../hooks/useAuth'
import { apiGet } from '../services/api'

export default function DashboardPage() {
  const { logout } = useAuth()
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    apiGet('/central/api/configs/test')
      .then((res) => setData(res))
      .catch((err) => setError(err.message))
  }, [])

  return (
    <div style={{ padding: '2rem', maxWidth: '960px', margin: '0 auto' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2rem' }}>
        <h1>Dashboard</h1>
        <button onClick={logout} style={{ padding: '0.5rem 1rem', cursor: 'pointer' }}>
          Logout
        </button>
      </div>

      <section style={{ background: '#f5f5f5', padding: '1.5rem', borderRadius: '8px' }}>
        <h2>API Response from /central/api/configs/test</h2>
        {error && <p style={{ color: 'red' }}>Error: {error}</p>}
        {data !== null && data !== undefined ? (
          <pre style={{ background: '#e8e8e8', padding: '1rem', borderRadius: '4px', overflowX: 'auto' }}>
            {typeof data === 'string' ? data : JSON.stringify(data, null, 2)}
          </pre>
        ) : (
          !error && <p>Loading API data...</p>
        )}
      </section>
    </div>
  )
}
