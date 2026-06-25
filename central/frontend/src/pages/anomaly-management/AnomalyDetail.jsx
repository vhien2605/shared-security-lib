import { anomalyChipClass, formatAnomalyTimestamp, formatAnomalyValue } from './anomalyMappers'

export default function AnomalyDetail({ anomaly, detail, isLoading, error, onClose }) {
  const current = detail || anomaly
  if (!current) {
    return (
      <aside className="anomaly-detail anomaly-detail--empty">
        <span className="material-symbols-outlined">info</span>
        <p>Chọn một bất thường để xem chi tiết.</p>
      </aside>
    )
  }

  return (
    <aside className="anomaly-detail">
      <header className="anomaly-detail__header">
        <div>
          <p>Chi tiết bất thường</p>
          <h2>{formatAnomalyValue(current.anomalyId)}</h2>
        </div>
        <button type="button" className="icon-button" onClick={onClose}><span className="material-symbols-outlined">close</span></button>
      </header>
      {isLoading ? <div className="anomaly-detail__state">Đang tải chi tiết...</div> : null}
      {error ? <div className="anomaly-detail__state anomaly-detail__state--error">{error}</div> : null}
      <div className="anomaly-detail__badges">
        <span className={anomalyChipClass(current.anomalyLevel, 'level')}>{formatAnomalyValue(current.anomalyLevel)}</span>
        <span className={anomalyChipClass(current.decision, 'decision')}>{formatAnomalyValue(current.decision)}</span>
        <span className="anomaly-chip">Risk {formatAnomalyValue(current.riskScore)}</span>
        <span className="anomaly-chip">{formatAnomalyValue(current.status)}</span>
      </div>
      <DetailSection title="Identity" rows={[
        ['Incident ID', current.incidentId], ['Trace ID', current.traceId], ['Correlation ID', current.correlationId], ['Source type', current.sourceType],
      ]} />
      <DetailSection title="Service / Endpoint" rows={[
        ['Service', current.serviceName || current.serviceId], ['Service ID', current.serviceId], ['Endpoint', current.endpointName || current.endpointId], ['Endpoint ID', current.endpointId], ['Flow', current.flowType], ['Direction', current.direction],
      ]} />
      <DetailSection title="Detection" rows={[
        ['Type', current.anomalyType], ['Confidence', current.confidence], ['Matched rules', (current.matchedRules || []).join(', ')], ['Detected features', (current.detectedFeatures || []).join(', ')],
      ]} />
      <DetailSection title="Risk / Incident" rows={[
        ['Risk score', current.riskScore], ['Max risk score', current.maxRiskScore], ['Max severity', current.maxSeverity], ['Matched count', current.matchedCount],
      ]} />
      <DetailSection title="Rolling window & baseline" rows={[
        ['Window start', formatAnomalyTimestamp(current.windowStart)], ['Window end', formatAnomalyTimestamp(current.windowEnd)], ['Window sample count', current.windowSampleCount], ['Rule set version', current.ruleSetVersion], ['Log baseline', current.logBaselineVersion], ['Behavior baseline', current.behaviorBaselineVersion],
      ]} />
      <DetailSection title="Timeline" rows={[
        ['Timestamp', formatAnomalyTimestamp(current.timestamp)], ['First seen', formatAnomalyTimestamp(current.firstSeenAt)], ['Last seen', formatAnomalyTimestamp(current.lastSeenAt)], ['Created at', formatAnomalyTimestamp(current.createdAt)],
      ]} />
      <section className="anomaly-detail__section">
        <h3>Feature snapshot</h3>
        <pre>{JSON.stringify(current.effectiveFeatureSnapshot || current.latestFeatureSnapshot || current.featureSnapshot || {}, null, 2)}</pre>
      </section>
    </aside>
  )
}

function DetailSection({ title, rows }) {
  return (
    <section className="anomaly-detail__section">
      <h3>{title}</h3>
      <dl>{rows.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{formatAnomalyValue(value)}</dd></div>)}</dl>
    </section>
  )
}
