import { AUTO_REFRESH_OPTIONS } from './securityLogConstants'
import { formatTimestamp } from './securityLogMappers'

export default function SecurityLogToolbar({ totalElements, lastUpdatedAt, autoRefreshMs, onAutoRefreshChange, onRefresh, isBusy }) {
  return (
    <div className="security-log-toolbar">
      <div>
        <span className="security-log-toolbar__count">{totalElements.toLocaleString('vi-VN')} bản ghi</span>
        <span className="security-log-toolbar__updated">Cập nhật: {formatTimestamp(lastUpdatedAt)}</span>
      </div>
      <div className="security-log-toolbar__actions">
        <label>
          Auto-refresh
          <select value={autoRefreshMs} onChange={(event) => onAutoRefreshChange(Number(event.target.value))}>
            {AUTO_REFRESH_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
          </select>
        </label>
        <button type="button" className="security-log-button" onClick={onRefresh} disabled={isBusy}>Refresh</button>
      </div>
    </div>
  )
}
