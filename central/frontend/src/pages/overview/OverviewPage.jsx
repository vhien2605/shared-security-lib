import { KIBANA_BASE_URL } from '../../constants'
import './OverviewPage.css'

const KIBANA_DASHBOARD_PATH = '/app/dashboards#/view/da1569e0-4411-442a-9fd7-d2d3e3e4975a?embed=true&_g=%28refreshInterval%3A%28pause%3A%21t%2Cvalue%3A60000%29%2Ctime%3A%28from%3Anow-12h%2Cto%3Anow%29%29&show-top-menu=true&show-query-input=true&show-time-filter=true'

export default function OverviewPage() {
  const dashboardSrc = `${KIBANA_BASE_URL}${KIBANA_DASHBOARD_PATH}`

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

        <div className="overview-dashboard-card">
          <iframe
            className="overview-dashboard-card__iframe"
            src={dashboardSrc}
            title="Biểu đồ tổng quan Kibana"
            height="600"
            width="800"
            loading="lazy"
          />
        </div>
      </div>
    </section>
  )
}
