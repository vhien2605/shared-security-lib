import { useEffect, useState } from 'react'
import { KIBANA_BASE_URL } from '../../constants'
import './OverviewPage.css'

const KIBANA_DASHBOARD_PATH = '/app/dashboards#/view/9d6706c0-b94d-4f16-92f3-62c1be0f2a73?embed=true&_g=%28refreshInterval%3A%28pause%3A%21t%2Cvalue%3A60000%29%2Ctime%3A%28from%3Anow-1h%2Cto%3Anow%29%29&show-top-menu=true&show-query-input=true&show-time-filter=true'

export default function OverviewPage() {
  const [isDashboardLoaded, setIsDashboardLoaded] = useState(false)
  const [shouldRenderDashboard, setShouldRenderDashboard] = useState(true)
  const dashboardSrc = `${KIBANA_BASE_URL}${KIBANA_DASHBOARD_PATH}`

  useEffect(() => {
    const teardownDashboard = () => setShouldRenderDashboard(false)

    window.addEventListener('app:navigation-start', teardownDashboard)

    return () => {
      window.removeEventListener('app:navigation-start', teardownDashboard)
    }
  }, [])

  return (
    <section className="overview-page">
      <div className="overview-container">
        <header className="overview-page-header">
          <h2>Tổng quan</h2>
          <p>
            Trang tổng quan cung cấp góc nhìn nhanh về tình trạng hệ thống, nhật ký bảo mật
            và các chỉ số vận hành quan trọng trong khoảng thời gian gần nhất.
          </p>
        </header>

        <div className="overview-dashboard-card" aria-busy={!isDashboardLoaded}>
          {!isDashboardLoaded && (
            <div className="overview-dashboard-card__loading">
              <span className="overview-dashboard-card__spinner" aria-hidden="true" />
              <span>Đang tải Kibana dashboard...</span>
            </div>
          )}
          {shouldRenderDashboard && (
            <iframe
              className={
                isDashboardLoaded
                  ? 'overview-dashboard-card__iframe overview-dashboard-card__iframe--loaded'
                  : 'overview-dashboard-card__iframe'
              }
              src={dashboardSrc}
              title="Biểu đồ tổng quan Kibana"
              height="600"
              width="800"
              loading="lazy"
              onLoad={() => setIsDashboardLoaded(true)}
            />
          )}
        </div>
      </div>
    </section>
  )
}
