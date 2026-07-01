import { useState } from 'react'
import { formatAnomalyNumber, formatAnomalyTimestamp } from './anomalyMappers'

function formatAnomalyTimestampShort(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  const now = new Date()
  const diffDays = (now - date) / 86400000
  if (diffDays < 1) return date.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })
  if (diffDays < 7) return date.toLocaleDateString('vi-VN', { weekday: 'short', hour: '2-digit' })
  return date.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit' })
}

const SVG_W = 800
const SVG_H = 200
const PAD = { top: 16, right: 16, bottom: 40, left: 44 }
const CHART_W = SVG_W - PAD.left - PAD.right
const CHART_H = SVG_H - PAD.top - PAD.bottom

function buildPoints(timeline, key, maxVal) {
  if (timeline.length === 0) return ''
  return timeline
    .map((b, i) => {
      const x = PAD.left + (i / Math.max(timeline.length - 1, 1)) * CHART_W
      const y = PAD.top + CHART_H - (Number(b[key] ?? 0) / maxVal) * CHART_H
      return `${x.toFixed(1)},${y.toFixed(1)}`
    })
    .join(' ')
}

function buildAreaPoints(timeline, key, maxVal) {
  if (timeline.length === 0) return ''
  const line = buildPoints(timeline, key, maxVal)
  const lastX = (PAD.left + CHART_W).toFixed(1)
  const firstX = PAD.left.toFixed(1)
  const baseY = (PAD.top + CHART_H).toFixed(1)
  return `${firstX},${baseY} ${line} ${lastX},${baseY}`
}

function TimelineLineChart({ timeline }) {
  const [tooltip, setTooltip] = useState(null)
  const maxVal = Math.max(1, ...timeline.map((b) => Math.max(Number(b.critical ?? 0), Number(b.high ?? 0))))
  const yTicks = [0, 0.25, 0.5, 0.75, 1]

  return (
    <div className="anomaly-line-chart-wrap">
      <svg
        viewBox={`0 0 ${SVG_W} ${SVG_H}`}
        className="anomaly-line-chart-svg"
        aria-label="Biểu đồ đường lưu lượng bất thường"
      >
        {/* Y grid lines + labels */}
        {yTicks.map((t) => {
          const y = PAD.top + CHART_H - t * CHART_H
          const label = Math.round(t * maxVal)
          return (
            <g key={t}>
              <line x1={PAD.left} x2={PAD.left + CHART_W} y1={y} y2={y} className="anomaly-line-chart__grid" />
              <text x={PAD.left - 6} y={y + 4} className="anomaly-line-chart__axis-label" textAnchor="end">{label}</text>
            </g>
          )
        })}

        {/* X axis labels — hiện mỗi N nhãn để không chen */}
        {timeline.map((b, i) => {
          const step = Math.ceil(timeline.length / 8)
          if (i % step !== 0 && i !== timeline.length - 1) return null
          const x = PAD.left + (i / Math.max(timeline.length - 1, 1)) * CHART_W
          return (
            <text key={b.bucket} x={x} y={SVG_H - 6} className="anomaly-line-chart__axis-label" textAnchor="middle">
              {formatAnomalyTimestampShort(b.bucket)}
            </text>
          )
        })}

        {/* Area fills */}
        <polygon points={buildAreaPoints(timeline, 'high', maxVal)} className="anomaly-line-chart__area anomaly-line-chart__area--high" />
        <polygon points={buildAreaPoints(timeline, 'critical', maxVal)} className="anomaly-line-chart__area anomaly-line-chart__area--critical" />

        {/* Lines */}
        <polyline points={buildPoints(timeline, 'high', maxVal)} className="anomaly-line-chart__line anomaly-line-chart__line--high" />
        <polyline points={buildPoints(timeline, 'critical', maxVal)} className="anomaly-line-chart__line anomaly-line-chart__line--critical" />

        {/* Hover hit areas + dots */}
        {timeline.map((b, i) => {
          const x = PAD.left + (i / Math.max(timeline.length - 1, 1)) * CHART_W
          const yC = PAD.top + CHART_H - (Number(b.critical ?? 0) / maxVal) * CHART_H
          const yH = PAD.top + CHART_H - (Number(b.high ?? 0) / maxVal) * CHART_H
          const isActive = tooltip?.index === i
          return (
            <g key={b.bucket}
              onMouseEnter={() => setTooltip({ index: i, x, bucket: b })}
              onMouseLeave={() => setTooltip(null)}
            >
              <rect x={x - 12} y={PAD.top} width={24} height={CHART_H} fill="transparent" />
              {isActive && <line x1={x} x2={x} y1={PAD.top} y2={PAD.top + CHART_H} className="anomaly-line-chart__crosshair" />}
              <circle cx={x} cy={yC} r={isActive ? 5 : 3} className="anomaly-line-chart__dot anomaly-line-chart__dot--critical" />
              <circle cx={x} cy={yH} r={isActive ? 5 : 3} className="anomaly-line-chart__dot anomaly-line-chart__dot--high" />
            </g>
          )
        })}
      </svg>

      {/* Tooltip */}
      {tooltip && (() => {
        const b = tooltip.bucket
        const pct = tooltip.x / SVG_W
        return (
          <div
            className="anomaly-line-chart__tooltip"
            style={{ left: `${Math.min(pct * 100, 80)}%` }}
          >
            <div className="anomaly-line-chart__tooltip-time">{formatAnomalyTimestamp(b.bucket)}</div>
            <div className="anomaly-line-chart__tooltip-row">
              <span className="critical-dot" /> Critical: <strong>{b.critical ?? 0}</strong>
            </div>
            <div className="anomaly-line-chart__tooltip-row">
              <span className="high-dot" /> High: <strong>{b.high ?? 0}</strong>
            </div>
            <div className="anomaly-line-chart__tooltip-row anomaly-line-chart__tooltip-total">
              Total: <strong>{b.total ?? 0}</strong>
            </div>
          </div>
        )
      })()}
    </div>
  )
}

export default function AnomalyCharts({ statistics, isLoading }) {
  const timeline = statistics?.timeline ?? []
  return (
    <section className="anomaly-charts-grid">
      <div className="anomaly-panel anomaly-chart anomaly-chart--wide">
        <header className="anomaly-chart__header">
          <h2>Lưu lượng bất thường theo thời gian</h2>
          <div className="anomaly-chart__legend"><span className="critical" /> Critical <span className="high" /> High</div>
        </header>
        {isLoading ? <p className="anomaly-muted">Đang tải biểu đồ...</p> : timeline.length === 0 ? <p className="anomaly-muted">Chưa có dữ liệu timeline.</p> : (
          <TimelineLineChart timeline={timeline} />
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
