import { useAuth } from '../hooks/useAuth'

export default function LoginPage() {
  const { login } = useAuth()

  return (
    <div style={{
      minHeight: '100vh',
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      justifyContent: 'center',
      background: 'linear-gradient(135deg, #0a5c2a 0%, #0d7a3a 50%, #0a5c2a 100%)',
      color: '#fff',
      fontFamily: 'system-ui, -apple-system, sans-serif',
    }}>
      <div style={{ textAlign: 'center' }}>
        <div style={{
          fontSize: '4rem',
          fontWeight: 800,
          letterSpacing: '0.1em',
          marginBottom: '0.5rem',
        }}>
          VIETTEL
        </div>
        <div style={{
          fontSize: '1.5rem',
          fontWeight: 300,
          opacity: 0.9,
          marginBottom: '3rem',
        }}>
          Hệ thống giám sát trung tâm
        </div>
        <button
          onClick={login}
          style={{
            padding: '0.85rem 3rem',
            fontSize: '1.1rem',
            fontWeight: 600,
            color: '#0a5c2a',
            background: '#fff',
            border: 'none',
            borderRadius: '8px',
            cursor: 'pointer',
            transition: 'transform 0.15s, box-shadow 0.15s',
            boxShadow: '0 4px 14px rgba(0,0,0,0.15)',
          }}
          onMouseEnter={(e) => {
            e.currentTarget.style.transform = 'translateY(-2px)'
            e.currentTarget.style.boxShadow = '0 6px 20px rgba(0,0,0,0.25)'
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.transform = 'translateY(0)'
            e.currentTarget.style.boxShadow = '0 4px 14px rgba(0,0,0,0.15)'
          }}
        >
          Đăng nhập
        </button>
      </div>
    </div>
  )
}
