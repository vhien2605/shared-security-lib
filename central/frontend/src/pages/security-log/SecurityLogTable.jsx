import { chipClass, formatDuration, formatTimestamp, formatValue } from './securityLogMappers'

function getLogKey(log, index = 0) {
  return log.id || `${log.traceId || 'log'}-${log.timestamp || index}`
}

export default function SecurityLogTable({ logs, selectedLog, onSelect, isLoading, error, onRetry }) {
  if (isLoading) return <div className="security-log-state">Đang tải nhật ký hệ thống...</div>
  if (error && logs.length === 0) {
    return (
      <div className="security-log-error" role="alert">
        <span>{error}</span>
        <button type="button" onClick={onRetry}>Thử lại</button>
      </div>
    )
  }
  if (logs.length === 0) return <div className="security-log-state">Không có log phù hợp. Hãy thử xóa bớt bộ lọc.</div>

  return (
    <div className="security-log-table-wrap">
      {error ? (
        <div className="security-log-error security-log-error--inline" role="alert">
          <span>{error}</span>
          <button type="button" onClick={onRetry}>Thử lại</button>
        </div>
      ) : null}
      <table className="security-log-table">
        <colgroup>
          <col className="security-log-table__col-time" />
          <col className="security-log-table__col-trace" />
          <col className="security-log-table__col-correlation" />
          <col className="security-log-table__col-flow" />
          <col className="security-log-table__col-direction" />
          <col className="security-log-table__col-service" />
          <col className="security-log-table__col-endpoint" />
          <col className="security-log-table__col-protocol" />
          <col className="security-log-table__col-method" />
          <col className="security-log-table__col-status" />
          <col className="security-log-table__col-result" />
          <col className="security-log-table__col-error" />
          <col className="security-log-table__col-duration" />
          <col className="security-log-table__col-threshold" />
          <col className="security-log-table__col-timeout" />
          <col className="security-log-table__col-retention-days" />
          <col className="security-log-table__col-retention-bucket" />
        </colgroup>
        <thead>
          <tr>
            <th>Timestamp</th><th>Trace</th><th>Correlation</th><th>Flow</th><th>Direction</th><th>Service</th><th>Endpoint</th>
            <th>Protocol</th><th>Method</th><th>Status</th><th>Result</th><th>Error</th><th>Duration</th><th>Threshold</th><th>Timeout</th><th>Retention</th><th>Bucket</th>
          </tr>
        </thead>
        <tbody>
          {logs.map((log, index) => {
            const rowKey = getLogKey(log, index)
            const selected = selectedLog && rowKey === getLogKey(selectedLog, index)
            const selectRow = () => onSelect(log)
            const handleKeyDown = (event) => {
              if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault()
                selectRow()
              }
            }
            return (
              <tr
                key={rowKey}
                className={selected ? 'security-log-table__row--selected' : ''}
                onClick={selectRow}
                onKeyDown={handleKeyDown}
                tabIndex={0}
                role="button"
                aria-label={`Mở chi tiết log ${log.traceId || log.timestamp || index + 1}`}
              >
                <td title={log.timestamp || ''}>{formatTimestamp(log.timestamp)}</td>
                <td title={log.traceId || ''} className="security-log-table__mono">{formatValue(log.traceId)}</td>
                <td title={log.correlationId || ''} className="security-log-table__mono">{formatValue(log.correlationId)}</td>
                <td><span className="security-log-chip">{formatValue(log.flowType)}</span></td>
                <td>{formatValue(log.direction)}</td>
                <td>{formatValue(log.serviceName || log.serviceId)}</td>
                <td>{formatValue(log.endpointName || log.endpointId)}</td>
                <td>{formatValue(log.protocol)}</td>
                <td>{formatValue(log.method)}</td>
                <td><span className={chipClass(log.status)}>{formatValue(log.status)}</span></td>
                <td>{formatValue(log.resultCode)}</td>
                <td>{formatValue(log.errorCode)}</td>
                <td>{formatDuration(log.durationMs)}</td>
                <td>{formatDuration(log.thresholdMs)}</td>
                <td>{formatDuration(log.timeoutMs)}</td>
                <td>{formatValue(log.retentionDays)}</td>
                <td>{formatValue(log.retentionBucket)}</td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
