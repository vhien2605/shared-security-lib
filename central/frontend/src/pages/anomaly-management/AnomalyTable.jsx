import { ANOMALY_PAGE_SIZE_OPTIONS } from './anomalyConstants'
import { anomalyChipClass, anomalyDisplayName, formatAnomalyTimestamp, formatAnomalyValue, riskScoreClass } from './anomalyMappers'

export default function AnomalyTable({ items, selectedAnomaly, pageInfo, isLoading, error, onSelect, onRetry, onPageChange, onSizeChange, disabled }) {
  const start = pageInfo.totalElements === 0 ? 0 : pageInfo.number * pageInfo.size + 1
  const end = Math.min(pageInfo.totalElements, (pageInfo.number + 1) * pageInfo.size)

  return (
    <section className="anomaly-panel anomaly-table-panel">
      <div className="anomaly-table-wrapper">
        <table className="anomaly-table">
          <thead>
            <tr>
              <th>Thời gian</th>
              <th>Anomaly ID</th>
              <th>Incident ID</th>
              <th>Mức độ</th>
              <th>Risk score</th>
              <th>Loại</th>
              <th>Service</th>
              <th>Endpoint</th>
              <th>Decision</th>
              <th>Matched</th>
            </tr>
          </thead>
          <tbody>
            {isLoading ? (
              <tr><td colSpan="10" className="anomaly-table__state">Đang tải danh sách bất thường...</td></tr>
            ) : error ? (
              <tr><td colSpan="10" className="anomaly-table__state anomaly-table__state--error">{error}<button type="button" onClick={onRetry}>Thử lại</button></td></tr>
            ) : items.length === 0 ? (
              <tr><td colSpan="10" className="anomaly-table__state">Không có bất thường phù hợp. Hãy thử xóa bớt bộ lọc.</td></tr>
            ) : items.map((item) => {
              const rowKey = item.anomalyId || item.incidentId || item.traceId
              const selectedKey = selectedAnomaly?.anomalyId || selectedAnomaly?.incidentId || selectedAnomaly?.traceId
              return (
                <tr
                  key={rowKey}
                  className={rowKey === selectedKey ? 'is-selected' : ''}
                  onClick={() => onSelect(item)}
                  onKeyDown={(event) => {
                    if (event.key === 'Enter' || event.key === ' ') {
                      event.preventDefault()
                      onSelect(item)
                    }
                  }}
                  tabIndex={0}
                  aria-selected={rowKey === selectedKey}
                >
                  <td className="mono-cell">{formatAnomalyTimestamp(item.timestamp)}</td>
                  <td className="mono-cell strong">{formatAnomalyValue(item.anomalyId)}</td>
                  <td className="mono-cell">{formatAnomalyValue(item.incidentId)}</td>
                  <td><span className={anomalyChipClass(item.anomalyLevel, 'level')}>{formatAnomalyValue(item.anomalyLevel)}</span></td>
                  <td><RiskScore score={item.riskScore} maxRiskScore={item.maxRiskScore} /></td>
                  <td>{formatAnomalyValue(item.anomalyType)}</td>
                  <td>{anomalyDisplayName(item, 'serviceName', 'serviceId')}</td>
                  <td>{anomalyDisplayName(item, 'endpointName', 'endpointId')}</td>
                  <td><span className={anomalyChipClass(item.decision, 'decision')}>{formatAnomalyValue(item.decision)}</span></td>
                  <td>{formatAnomalyValue(item.matchedCount)}</td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
      <footer className="anomaly-pagination">
        <span>Hiển thị {start} - {end} trên {pageInfo.totalElements.toLocaleString('vi-VN')} sự kiện</span>
        <label>
          Dòng/trang
          <select value={pageInfo.size} onChange={(event) => onSizeChange(Number(event.target.value))} disabled={disabled}>
            {ANOMALY_PAGE_SIZE_OPTIONS.map((size) => <option key={size} value={size}>{size}</option>)}
          </select>
        </label>
        <div className="anomaly-pagination__buttons">
          <button type="button" onClick={() => onPageChange(Math.max(0, pageInfo.number - 1))} disabled={disabled || pageInfo.first}>‹</button>
          <span>Trang {pageInfo.number + 1} / {Math.max(1, pageInfo.totalPages)}</span>
          <button type="button" onClick={() => onPageChange(pageInfo.number + 1)} disabled={disabled || pageInfo.last}>›</button>
        </div>
      </footer>
    </section>
  )
}

function RiskScore({ score, maxRiskScore }) {
  const value = Number(score ?? 0)
  const width = Math.max(0, Math.min(100, value))
  const title = maxRiskScore && maxRiskScore !== score ? `Max risk score: ${maxRiskScore}` : undefined
  return (
    <div className={riskScoreClass(value)} title={title}>
      <span className="anomaly-risk__bar"><span style={{ width: `${width}%` }} /></span>
      <strong>{Number.isFinite(value) ? value : '—'}</strong>
    </div>
  )
}
