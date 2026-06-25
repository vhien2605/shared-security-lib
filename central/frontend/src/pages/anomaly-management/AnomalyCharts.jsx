import { formatAnomalyNumber, formatAnomalyTimestamp } from './anomalyMappers'

export default function AnomalyCharts({ statistics, isLoading }) {
  const timeline = statistics?.timeline ?? []
  const maxTimeline = Math.max(1, ...timeline.map((bucket) => Number(bucket.total ?? 0)))
  return (
    <section className="anomaly-charts-grid">
      <div className="anomaly-panel anomaly-chart anomaly-chart--wide">
        <header className="anomaly-chart__header">
          <h2>Lưu lượng bất thường theo thời gian</h2>
          <div className="anomaly-chart__legend"><span className="critical" /> Critical <span className="high" /> High</div>
        </header>
        {isLoading ? <p className="anomaly-muted">Đang tải biểu đồ...</p> : timeline.length === 0 ? <p className="anomaly-muted">Chưa có dữ liệu timeline.</p> : (
          <div className="anomaly-timeline-chart">
            {timeline.map((bucket) => {
              const criticalHeight = Math.max(2, (Number(bucket.critical ?? 0) / maxTimeline) * 100)
              const highHeight = Math.max(2, (Number(bucket.high ?? 0) / maxTimeline) * 100)
              return (
                <div key={bucket.bucket} className="anomaly-timeline-chart__bar" title={`${formatAnomalyTimestamp(bucket.bucket)}: ${bucket.total}`}>
                  <span className="critical" style={{ height: `${criticalHeight}%` }} />
                  <span className="high" style={{ height: `${highHeight}%` }} />
                </div>
              )
            })}
          </div>
        )}
      </div>
      <BucketList title="Top services" buckets={statistics?.topServices} />
      <BucketList title="Top endpoints" buckets={statistics?.topEndpoints} />
      <BucketList title="Top rules" buckets={statistics?.topMatchedRules} />
    </section>
  )
}

function BucketList({ title, buckets = [] }) {
  const max = Math.max(1, ...buckets.map((bucket) => Number(bucket.count ?? 0)))
  return (
    <div className="anomaly-panel anomaly-buckets">
      <h2>{title}</h2>
      {buckets.length === 0 ? <p className="anomaly-muted">Không có dữ liệu.</p> : buckets.slice(0, 6).map((bucket) => (
        <div key={bucket.key} className="anomaly-bucket-row">
          <span>{bucket.key}</span>
          <strong>{formatAnomalyNumber(bucket.count)}</strong>
          <i style={{ width: `${(Number(bucket.count ?? 0) / max) * 100}%` }} />
        </div>
      ))}
    </div>
  )
}
