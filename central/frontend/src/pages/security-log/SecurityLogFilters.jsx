import {
  DEFAULT_FILTERS,
  DIRECTION_OPTIONS,
  FLOW_TYPE_OPTIONS,
  METHOD_OPTIONS,
  PROTOCOL_OPTIONS,
  SEVERITY_OPTIONS,
  STATUS_OPTIONS,
} from './securityLogConstants'

const TEXT_FILTERS = [
  ['serviceId', 'Service ID'], ['serviceName', 'Service name'], ['endpointId', 'Endpoint ID'], ['endpointName', 'Endpoint name'],
  ['resultCode', 'Result code'], ['errorCode', 'Error code'], ['clientId', 'Client ID'], ['clientKey', 'Client key'],
  ['traceId', 'Trace ID'], ['correlationId', 'Correlation ID'], ['target', 'Target path/url/topic'],
]

function SelectField({ id, label, value, options, onChange }) {
  return (
    <label className="security-log-filter__field">
      <span>{label}</span>
      <select id={id} value={value} onChange={(event) => onChange(id, event.target.value)}>
        <option value="">Tất cả</option>
        {options.map((option) => <option key={option} value={option}>{option}</option>)}
      </select>
    </label>
  )
}

export default function SecurityLogFilters({ filters, onChange, onApply, onReset, disabled }) {
  const updateFilter = (key, value) => onChange({ ...filters, [key]: value })

  return (
    <aside className="security-log-filters" aria-label="Bộ lọc nhật ký hệ thống">
      <div className="security-log-filters__header">
        <h2>Bộ lọc</h2>
        <button type="button" onClick={() => onChange(DEFAULT_FILTERS)} disabled={disabled}>Xóa nháp</button>
      </div>
      <div className="security-log-filter__grid">
        <label className="security-log-filter__field">
          <span>Từ thời điểm</span>
          <input type="datetime-local" value={filters.from} onChange={(event) => updateFilter('from', event.target.value)} />
        </label>
        <label className="security-log-filter__field">
          <span>Đến thời điểm</span>
          <input type="datetime-local" value={filters.to} onChange={(event) => updateFilter('to', event.target.value)} />
        </label>
        <SelectField id="flowType" label="Flow type" value={filters.flowType} options={FLOW_TYPE_OPTIONS} onChange={updateFilter} />
        <SelectField id="direction" label="Direction" value={filters.direction} options={DIRECTION_OPTIONS} onChange={updateFilter} />
        <SelectField id="protocol" label="Protocol" value={filters.protocol} options={PROTOCOL_OPTIONS} onChange={updateFilter} />
        <SelectField id="method" label="Method" value={filters.method} options={METHOD_OPTIONS} onChange={updateFilter} />
        <SelectField id="status" label="Status" value={filters.status} options={STATUS_OPTIONS} onChange={updateFilter} />
        <SelectField id="alertSeverity" label="Severity" value={filters.alertSeverity} options={SEVERITY_OPTIONS} onChange={updateFilter} />
        {TEXT_FILTERS.map(([key, label]) => (
          <label className="security-log-filter__field" key={key}>
            <span>{label}</span>
            <input value={filters[key]} onChange={(event) => updateFilter(key, event.target.value)} placeholder={label} />
          </label>
        ))}
      </div>
      <div className="security-log-filters__actions">
        <button type="button" className="security-log-button security-log-button--primary" onClick={onApply} disabled={disabled}>Tìm kiếm</button>
        <button type="button" className="security-log-button" onClick={onReset} disabled={disabled}>Xóa lọc</button>
      </div>
    </aside>
  )
}
