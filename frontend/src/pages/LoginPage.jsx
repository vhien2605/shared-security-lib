import { useState } from 'react'
import { useAuth } from '../hooks/useAuth'
import './LoginPage.css'

export default function LoginPage() {
  const { login, authError, clearAuthError } = useAuth()
  const [isSubmitting, setIsSubmitting] = useState(false)

  const handleSubmit = (event) => {
    event.preventDefault()
    clearAuthError()

    if (isSubmitting) return

    setIsSubmitting(true)
    login()
    setTimeout(() => setIsSubmitting(false), 2500)
  }

  return (
    <main className="login-page">
      <section className="login-page__hero" aria-label="Thông tin hệ thống">
        <div className="login-page__badge">Nền tảng giám sát tập trung</div>
        <div className="login-page__hero-brand">VDT Sentinel</div>
        <h1 className="login-page__hero-title">Hệ thống giám sát tập trung VDT 2026</h1>
        <p className="login-page__hero-subtitle">
          Bảo vệ hạ tầng, phân tích sự cố và phản hồi theo thời gian thực.
        </p>
        <div className="login-page__stats" aria-label="Chỉ số hệ thống">
          <article className="login-page__stat-card">
            <span className="login-page__stat-value">500+</span>
            <span className="login-page__stat-label">Đơn vị kết nối</span>
          </article>
          <article className="login-page__stat-card">
            <span className="login-page__stat-value">24/7</span>
            <span className="login-page__stat-label">Giám sát liên tục</span>
          </article>
          <article className="login-page__stat-card">
            <span className="login-page__stat-value">100%</span>
            <span className="login-page__stat-label">Phản hồi tự động</span>
          </article>
        </div>
        <p className="login-page__hero-note">Hệ thống bảo mật dữ liệu và xác thực đa lớp.</p>
      </section>

      <section className="login-page__auth" aria-label="Đăng nhập">
        <form className="login-form" onSubmit={handleSubmit} noValidate>
          <p className="login-form__logo" aria-hidden="true">
            🛡
          </p>
          <h2 className="login-form__title">Đăng nhập</h2>
          <p className="login-form__subtitle">
            Đăng nhập được quản lý bởi Keycloak để đảm bảo bảo mật và quản trị tài khoản tập trung.
          </p>

          {authError && (
            <div className="login-form__alert" role="alert" aria-live="assertive">
              Không thể khởi tạo đăng nhập. Vui lòng thử lại.
            </div>
          )}

          <button type="submit" className="login-form__submit" disabled={isSubmitting}>
            {isSubmitting ? 'Đang chuyển hướng...' : 'Đăng nhập với Keycloak'}
          </button>

          <p className="login-form__hint">Bạn sẽ được chuyển đến trang xác thực Keycloak.</p>
        </form>
      </section>
    </main>
  )
}
