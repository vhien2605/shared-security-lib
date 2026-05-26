import { useAuth } from '../hooks/useAuth'

export default function CallbackHandler() {
  const { authError, login } = useAuth()

  if (authError) {
    return (
      <div style={{ padding: '3rem', textAlign: 'center', fontFamily: 'system-ui, sans-serif' }}>
        <p style={{ color: '#c00', marginBottom: '1rem' }}>Đăng nhập thất bại: {authError}</p>
        <button
          onClick={login}
          style={{ padding: '0.5rem 2rem', cursor: 'pointer' }}
        >
          Thử lại
        </button>
      </div>
    )
  }

  return (
    <div style={{ padding: '3rem', textAlign: 'center', fontFamily: 'system-ui, sans-serif', color: '#555' }}>
      Đang xử lý đăng nhập...
    </div>
  )
}
