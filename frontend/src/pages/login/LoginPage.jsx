import { useState } from 'react'
import { useAuth } from '../../hooks/useAuth'
import './LoginPage.css'

export default function LoginPage() {
  const { login, authError, clearAuthError } = useAuth()
  const [isSubmitting, setIsSubmitting] = useState(false)

  const handleSubmit = async (event) => {
    event.preventDefault()
    clearAuthError()

    if (isSubmitting) return

    setIsSubmitting(true)

    try {
      await login()
    } catch {
      setIsSubmitting(false)
    }
  }

  return (
    <main className="login-page">
      <section className="login-page__hero" aria-label="Thông tin hệ thống">
        <div className="login-page__brand-wrap">
          <div className="login-page__hero-logo" aria-hidden="true">
            🛡
          </div>
          <div>
            <p className="login-page__hero-brand">VDT Sentinel</p>
            <p className="login-page__hero-brand-sub">Hệ thống giám sát tập trung</p>
          </div>
        </div>

        <div className="login-page__badge">Giải pháp an ninh chọn lọc tại Hà Nội</div>
        <h1 className="login-page__hero-title">Khám phá không gian an toàn dành cho bạn</h1>
        <p className="login-page__hero-subtitle">
          Hệ thống giám sát tập trung VDT 2026 - Bảo vệ tài sản và con người mỗi ngày.
        </p>
        <div className="login-page__stats" aria-label="Chỉ số hệ thống">
          <article className="login-page__stat-card">
            <span className="login-page__stat-value">500+</span>
            <span className="login-page__stat-label">Thiết bị</span>
          </article>
          <article className="login-page__stat-card">
            <span className="login-page__stat-value">24/7</span>
            <span className="login-page__stat-label">Hỗ trợ</span>
          </article>
          <article className="login-page__stat-card">
            <span className="login-page__stat-value">100%</span>
            <span className="login-page__stat-label">Xác minh</span>
          </article>
        </div>
        <p className="login-page__hero-note">Thông tin bảo mật và dữ liệu được bảo vệ an toàn.</p>
      </section>

      <section className="login-page__auth" aria-label="Đăng nhập">
        <form className="login-form" onSubmit={handleSubmit} noValidate>
          <div className="login-form__brand-wrap">
            <p className="login-form__logo" aria-hidden="true">
              🛡
            </p>
            <div>
              <p className="login-form__brand-title">VDT Sentinel</p>
              <p className="login-form__brand-subtitle">Hệ thống giám sát tập trung VDT 2026</p>
            </div>
          </div>

          <span className="login-form__member-badge">Thành viên</span>
          <h2 className="login-form__title">Đăng nhập</h2>
          <p className="login-form__subtitle">Sử dụng tài khoản hệ thống của bạn</p>

          {authError && (
            <div className="login-form__alert" role="alert" aria-live="assertive">
              Đăng nhập thất bại. Vui lòng thử lại.
            </div>
          )}

          <button type="submit" className="login-form__submit" disabled={isSubmitting}>
            {isSubmitting ? 'Đang chuyển đến Keycloak...' : 'ĐĂNG NHẬP HỆ THỐNG'}
          </button>

          <p className="login-form__hint">Xác thực qua cổng bảo mật Keycloak.</p>
        </form>
      </section>
    </main>
  )
}
