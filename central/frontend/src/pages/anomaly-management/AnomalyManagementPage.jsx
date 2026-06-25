import useAnomalyManagement from '../../hooks/useAnomalyManagement'
import AnomalyCharts from './AnomalyCharts'
import AnomalyDetail from './AnomalyDetail'
import AnomalyTable from './AnomalyTable'
import {
  ANOMALY_AUTO_REFRESH_OPTIONS,
  ANOMALY_DECISION_OPTIONS,
  ANOMALY_DIRECTION_OPTIONS,
  ANOMALY_FLOW_TYPE_OPTIONS,
  ANOMALY_LEVEL_OPTIONS,
  ANOMALY_SOURCE_TYPE_OPTIONS,
  ANOMALY_TYPE_OPTIONS,
  DEFAULT_ANOMALY_FILTERS,
} from './anomalyConstants'
import { formatAnomalyNumber, formatAnomalyTimestamp } from './anomalyMappers'
import './AnomalyManagementPage.css'

export default function AnomalyManagementPage() {
  const state = useAnomalyManagement()
  const busy = state.isInitialLoading || state.isRefreshing

  return (
    <main className="anomaly-management-page">
      <div className="anomaly-management-container">
        <header className="anomaly-management-page__header">
          <div>
            <h1>Quản lý bất thường</h1>
            <p>Giám sát anomaly và incident từ Elasticsearch theo service, endpoint, risk score và trace.</p>
          </div>
          {state.isRefreshing ? <span className="anomaly-management-page__refreshing">Đang refresh...</span> : null}
        </header>

        <SummaryCards statistics={state.statistics} isLoading={state.statisticsLoading} />

        {state.statisticsError && !state.error ? <div className="anomaly-alert anomaly-alert--warning">{state.statisticsError}</div> : null}

        <div className="anomaly-management-page__layout">
          <FilterPanel
            filters={state.draftFilters}
            onChange={state.setDraftFilters}
            onApply={state.applyFilters}
            onReset={state.resetFilters}
            validationError={state.filterValidationError}
            disabled={busy}
          />
          <section className="anomaly-management-page__main">
            <Toolbar
              totalElements={state.pageInfo.totalElements}
              lastUpdatedAt={state.lastUpdatedAt}
              autoRefreshMs={state.autoRefreshMs}
              onAutoRefreshChange={state.setAutoRefreshMs}
              onRefresh={state.refresh}
              isBusy={busy}
            />
            <AnomalyTable
              items={state.items}
              selectedAnomaly={state.selectedAnomaly}
              pageInfo={state.pageInfo}
              isLoading={state.isInitialLoading}
              error={state.error}
              onSelect={state.selectAnomaly}
              onRetry={state.refresh}
              onPageChange={state.changePage}
              onSizeChange={state.changePageSize}
              disabled={busy}
            />
            <AnomalyCharts statistics={state.statistics} isLoading={state.statisticsLoading} />
          </section>
          <AnomalyDetail
            anomaly={state.selectedAnomaly}
            detail={state.detail}
            isLoading={state.detailLoading}
            error={state.detailError}
            onClose={state.closeDetail}
          />
        </div>
      </div>
    </main>
  )
}

function SummaryCards({ statistics, isLoading }) {
  const averageRiskScore = Number(statistics?.averageRiskScore ?? 0)
  const riskWidth = Math.max(0, Math.min(100, averageRiskScore))
  const cards = [
    { icon: 'security_update_warning', label: 'Tổng số bất thường', value: formatAnomalyNumber(statistics?.totalAnomalies), hint: 'Theo bộ lọc hiện tại' },
    { icon: 'priority_high', label: 'Cảnh báo critical', value: formatAnomalyNumber(statistics?.criticalAnomalies), hint: 'Cần xử lý ngay', danger: true },
    { icon: 'monitoring', label: 'Điểm rủi ro trung bình', value: `${averageRiskScore.toLocaleString('vi-VN')} / 100`, hint: 'Risk score trung bình', progress: riskWidth },
    { icon: 'hub', label: 'Incident duy nhất', value: formatAnomalyNumber(statistics?.totalIncidents), hint: `${formatAnomalyNumber(statistics?.affectedServices)} service ảnh hưởng` },
  ]

  return (
    <section className="anomaly-summary-grid" aria-label="Thống kê bất thường">
      {cards.map((card) => (
        <article key={card.label} className={`anomaly-summary-card${card.danger ? ' anomaly-summary-card--danger' : ''}`}>
          <div className="anomaly-summary-card__top">
            <span className="material-symbols-outlined">{card.icon}</span>
            {isLoading ? <small>Đang tải</small> : null}
          </div>
          <p>{card.label}</p>
          <h2>{isLoading ? '...' : card.value}</h2>
          {card.progress !== undefined ? <i className="anomaly-summary-card__progress"><span style={{ width: `${card.progress}%` }} /></i> : null}
          <small>{card.hint}</small>
        </article>
      ))}
    </section>
  )
}

function FilterPanel({ filters, onChange, onApply, onReset, validationError, disabled }) {
  const update = (field, value) => onChange((current) => ({ ...current, [field]: value }))
  const clearDraft = () => onChange(() => ({ ...DEFAULT_ANOMALY_FILTERS }))

  return (
    <aside className="anomaly-filter-panel" aria-label="Bộ lọc bất thường">
      <div className="anomaly-filter-panel__header">
        <h2>Bộ lọc</h2>
        <button type="button" onClick={clearDraft} disabled={disabled}>Xóa nháp</button>
      </div>
      <div className="anomaly-filter-panel__grid">
        <InputField label="Từ thời điểm" type="datetime-local" value={filters.from} onChange={(value) => update('from', value)} />
        <InputField label="Đến thời điểm" type="datetime-local" value={filters.to} onChange={(value) => update('to', value)} />
        <InputField label="Service ID" value={filters.serviceId} onChange={(value) => update('serviceId', value)} placeholder="service-id" />
        <InputField label="Endpoint ID" value={filters.endpointId} onChange={(value) => update('endpointId', value)} placeholder="endpoint-id" />
        <SelectField label="Loại bất thường" value={filters.anomalyType} options={ANOMALY_TYPE_OPTIONS} onChange={(value) => update('anomalyType', value)} />
        <SelectField label="Mức độ" value={filters.anomalyLevel} options={ANOMALY_LEVEL_OPTIONS} onChange={(value) => update('anomalyLevel', value)} />
        <SelectField label="Decision" value={filters.decision} options={ANOMALY_DECISION_OPTIONS} onChange={(value) => update('decision', value)} />
        <SelectField label="Source type" value={filters.sourceType} options={ANOMALY_SOURCE_TYPE_OPTIONS} onChange={(value) => update('sourceType', value)} />
        <SelectField label="Flow type" value={filters.flowType} options={ANOMALY_FLOW_TYPE_OPTIONS} onChange={(value) => update('flowType', value)} />
        <SelectField label="Direction" value={filters.eventDirection} options={ANOMALY_DIRECTION_OPTIONS} onChange={(value) => update('eventDirection', value)} />
        <InputField label="Incident ID" value={filters.incidentId} onChange={(value) => update('incidentId', value)} />
        <InputField label="Trace ID" value={filters.traceId} onChange={(value) => update('traceId', value)} />
        <InputField label="Risk min" type="number" value={filters.minRiskScore} min="0" max="100" onChange={(value) => update('minRiskScore', value)} />
        <InputField label="Risk max" type="number" value={filters.maxRiskScore} min="0" max="100" onChange={(value) => update('maxRiskScore', value)} />
      </div>
      {validationError ? <div className="anomaly-alert anomaly-alert--error">{validationError}</div> : null}
      <div className="anomaly-filter-panel__actions">
        <button type="button" className="anomaly-button anomaly-button--primary" onClick={onApply} disabled={disabled || Boolean(validationError)}>Tìm kiếm</button>
        <button type="button" className="anomaly-button" onClick={onReset} disabled={disabled}>Xóa lọc</button>
      </div>
    </aside>
  )
}

function Toolbar({ totalElements, lastUpdatedAt, autoRefreshMs, onAutoRefreshChange, onRefresh, isBusy }) {
  return (
    <section className="anomaly-toolbar">
      <div>
        <strong>{formatAnomalyNumber(totalElements)} bản ghi</strong>
        <span>Cập nhật: {lastUpdatedAt ? formatAnomalyTimestamp(lastUpdatedAt) : 'Chưa có dữ liệu'}</span>
      </div>
      <div className="anomaly-toolbar__actions">
        <label>
          Tự động refresh
          <select value={autoRefreshMs} onChange={(event) => onAutoRefreshChange(Number(event.target.value))} disabled={isBusy}>
            {ANOMALY_AUTO_REFRESH_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
          </select>
        </label>
        <button type="button" className="anomaly-button" onClick={onRefresh} disabled={isBusy}>Refresh</button>
      </div>
    </section>
  )
}

function InputField({ label, value, onChange, ...props }) {
  return (
    <label className="anomaly-field">
      {label}
      <input value={value} onChange={(event) => onChange(event.target.value)} {...props} />
    </label>
  )
}

function SelectField({ label, value, options, onChange }) {
  return (
    <label className="anomaly-field">
      {label}
      <select value={value} onChange={(event) => onChange(event.target.value)}>
        <option value="">Tất cả</option>
        {options.map((option) => <option key={option} value={option}>{option}</option>)}
      </select>
    </label>
  )
}
