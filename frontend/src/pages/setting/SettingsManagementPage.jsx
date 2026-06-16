import { useNavigate } from 'react-router-dom'
import { useSettingsManagement } from '../../hooks/useSettingsManagement'
import { ROLLBACK_STRATEGIES, textValue } from '../../services/settings'
import './SettingsManagementPage.css'

const CHANNELS = ['SLACK', 'EMAIL', 'WEBHOOK']

function serviceStatusClass(status) {
  if (status === 'ACTIVE') return 'settings-status settings-status--active'
  if (status === 'DEPRECATED') return 'settings-status settings-status--maintenance'
  return 'settings-status settings-status--inactive'
}

function serviceDotClass(status) {
  if (status === 'ACTIVE') return 'settings-service-dot settings-service-dot--active'
  if (status === 'DEPRECATED') return 'settings-service-dot settings-service-dot--maintenance'
  return 'settings-service-dot settings-service-dot--inactive'
}

function SettingInput({ label, value, onChange, unit = '', className = '' }) {
  return (
    <div className="settings-field-row">
      <span>{label}</span>
      <div className="settings-config-input-wrap">
        <input
          type="text"
          className={`settings-config-input ${className}`.trim()}
          value={value}
          onChange={(event) => onChange(event.target.value.replace(/[^0-9-]/g, ''))}
        />
        {unit ? <span>{unit}</span> : null}
      </div>
    </div>
  )
}

export default function SettingsManagementPage() {
  const navigate = useNavigate()
  const {
    services,
    pageInfo,
    template,
    applyMode,
    isLoading,
    isSaving,
    togglingServiceId,
    message,
    stats,
    setApplyMode,
    updateTemplate,
    toggleChannel,
    handleSave,
    handleToggleService,
  } = useSettingsManagement()

  return (
    <section className="settings-page">
      <div className="settings-container">
        <div className="settings-page-header">
          <div>
            <h2>Danh sách dịch vụ hệ thống</h2>
            <p>Quản lý và cấu hình các dịch vụ bảo mật trong hệ thống Secure Service.</p>
          </div>
        </div>

        <div className="settings-stats-grid">
          <div className="settings-stat-card">
            <div className="settings-stat-icon settings-stat-icon--primary">
              <span className="material-symbols-outlined">dns</span>
            </div>
            <div>
              <p>TỔNG DỊCH VỤ</p>
              <strong>{stats.total}</strong>
            </div>
          </div>
          <div className="settings-stat-card">
            <div className="settings-stat-icon settings-stat-icon--green">
              <span className="material-symbols-outlined settings-icon-fill">check_circle</span>
            </div>
            <div>
              <p>ĐANG HOẠT ĐỘNG</p>
              <strong>{stats.active}</strong>
            </div>
          </div>
          <div className="settings-stat-card">
            <div className="settings-stat-icon settings-stat-icon--surface">
              <span className="material-symbols-outlined">error</span>
            </div>
            <div>
              <p>TẠM DỪNG</p>
              <strong>{stats.inactive}</strong>
            </div>
          </div>
        </div>

        <div className="settings-table-card">
          <div className="settings-table-card__header">
            <h3>SecureService List</h3>
            <div className="settings-action-group">
              <button type="button" className="settings-icon-button" aria-label="Lọc">
                <span className="material-symbols-outlined">filter_list</span>
              </button>
              <button type="button" className="settings-icon-button" aria-label="Tải xuống">
                <span className="material-symbols-outlined">download</span>
              </button>
            </div>
          </div>
          <div className="settings-table-wrap">
            <table className="settings-table">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Name</th>
                  <th>Description</th>
                  <th>Base URL</th>
                  <th>Status</th>
                  <th className="settings-text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {services.map((service) => (
                  <tr key={service.id}>
                    <td>
                      <code>{service.id}</code>
                    </td>
                    <td>
                      <div className="settings-service-name">
                        <div className={serviceDotClass(service.status)} />
                        <span>{service.name}</span>
                      </div>
                    </td>
                    <td className="settings-muted-cell">{service.description}</td>
                    <td>
                      <code>{service.baseUrl}</code>
                    </td>
                    <td>
                      <span className={serviceStatusClass(service.status)}>{service.status}</span>
                    </td>
                    <td className="settings-text-right">
                      <div className="settings-row-actions">
                        <button
                          type="button"
                          className={service.status === 'ACTIVE' ? 'settings-toggle-button settings-toggle-button--inactive' : 'settings-toggle-button settings-toggle-button--active'}
                          onClick={() => handleToggleService(service)}
                          disabled={!service.id || service.status === 'DEPRECATED' || togglingServiceId === service.id}
                        >
                          {togglingServiceId === service.id ? 'Đang cập nhật...' : service.status === 'ACTIVE' ? 'Tắt' : 'Bật'}
                        </button>
                        <button
                          type="button"
                          className="settings-view-button"
                          aria-label="Xem dịch vụ"
                          onClick={() => navigate(`/settings-management/services/${service.id}`, { state: { service } })}
                          disabled={!service.id}
                        >
                          <span className="material-symbols-outlined">visibility</span>
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
                {!isLoading && services.length === 0 ? (
                  <tr>
                    <td colSpan="6" className="settings-empty-cell">
                      Không có dịch vụ.
                    </td>
                  </tr>
                ) : null}
              </tbody>
            </table>
          </div>
          <div className="settings-pagination">
            <p>
              Hiển thị {services.length > 0 ? pageInfo.number * pageInfo.size + 1 : 0} -{' '}
              {pageInfo.number * pageInfo.size + services.length} trong tổng số {stats.total} dịch vụ
            </p>
            <div className="settings-pagination__buttons">
              <button type="button" disabled={pageInfo.first}>Trước</button>
              <button type="button" className="settings-page-active">{pageInfo.number + 1}</button>
              <button type="button">{pageInfo.number + 2}</button>
              <button type="button" disabled={pageInfo.last}>Tiếp</button>
            </div>
          </div>
        </div>

        <section className="settings-global-card">
          <h3>Thông số cài đặt Global</h3>
          <div className="settings-global-grid">
            <div className="settings-config-box">
              <h4>Inbound Configuration</h4>
              <div className="settings-config-fields">
                <SettingInput label="Rate Limit:" value={textValue(template.inboundRateLimit)} unit="req/s" onChange={(value) => updateTemplate('inboundRateLimit', value)} />
                <SettingInput label="Window:" value={textValue(template.inboundRateLimitWindowSeconds)} unit="s" onChange={(value) => updateTemplate('inboundRateLimitWindowSeconds', value)} />
                <SettingInput label="Timeout:" value={textValue(template.inboundTimeoutMs)} unit="ms" onChange={(value) => updateTemplate('inboundTimeoutMs', value)} />
                <SettingInput label="Req Size:" value={textValue(template.inboundRequestSizeLimitKb)} unit="KB" onChange={(value) => updateTemplate('inboundRequestSizeLimitKb', value)} />
                <SettingInput label="Res Size:" value={textValue(template.inboundResponseSizeLimitKb)} unit="KB" onChange={(value) => updateTemplate('inboundResponseSizeLimitKb', value)} />
                <SettingInput label="Threshold:" value={textValue(template.inboundResponseTimeThresholdMs)} unit="ms" onChange={(value) => updateTemplate('inboundResponseTimeThresholdMs', value)} />
                <SettingInput label="Log Retention:" value={textValue(template.inboundLogRetentionDays)} unit="days" onChange={(value) => updateTemplate('inboundLogRetentionDays', value)} />
              </div>
            </div>

            <div className="settings-config-box">
              <h4>Outbound Configuration</h4>
              <div className="settings-config-fields">
                <SettingInput label="Timeout:" value={textValue(template.outboundTimeoutMs)} unit="ms" onChange={(value) => updateTemplate('outboundTimeoutMs', value)} />
                <SettingInput label="Retry Count:" value={textValue(template.outboundRetryCount)} onChange={(value) => updateTemplate('outboundRetryCount', value)} />
                <SettingInput label="Backoff:" value={textValue(template.outboundRetryBackoffMs)} unit="ms" onChange={(value) => updateTemplate('outboundRetryBackoffMs', value)} />
                <SettingInput label="Threshold:" value={textValue(template.outboundResponseTimeThresholdMs)} unit="ms" onChange={(value) => updateTemplate('outboundResponseTimeThresholdMs', value)} />
                <SettingInput label="Log Retention:" value={textValue(template.outboundLogRetentionDays)} unit="days" onChange={(value) => updateTemplate('outboundLogRetentionDays', value)} />
                <div className="settings-field-row">
                  <span>Rollback:</span>
                  <select className="settings-config-select" value={template.outboundRollbackStrategy || ''} onChange={(event) => updateTemplate('outboundRollbackStrategy', event.target.value)}>
                    {ROLLBACK_STRATEGIES.map((option) => (
                      <option key={option}>{option}</option>
                    ))}
                  </select>
                </div>
              </div>
            </div>

            <div className="settings-alert-box">
              <div className="settings-alert-field">
                <span>Severity</span>
                <div className="settings-select-wrap">
                  <select value={template.alertSeverity || ''} onChange={(event) => updateTemplate('alertSeverity', event.target.value)}>
                    <option>INFO</option>
                    <option>WARNING</option>
                    <option>CRITICAL</option>
                  </select>
                  <span className="material-symbols-outlined">expand_more</span>
                </div>
              </div>
              <div className="settings-alert-field">
                <span>Throttle</span>
                <input
                  type="text"
                  className="settings-throttle-input"
                  value={textValue(template.alertThrottleMinutes, ' minutes')}
                  onChange={(event) => updateTemplate('alertThrottleMinutes', event.target.value)}
                />
              </div>
              <div className="settings-alert-field">
                <span className="settings-channel-label">Channels</span>
                <div className="settings-channels">
                  {CHANNELS.map((channel) => (
                    <button
                      key={channel}
                      type="button"
                      className={(template.alertChannels || []).includes(channel) ? 'settings-channel settings-channel--active' : 'settings-channel'}
                      onClick={() => toggleChannel(channel)}
                    >
                      {channel.toLowerCase()}
                    </button>
                  ))}
                </div>
              </div>
            </div>
          </div>

          <div className="settings-global-footer">
            <div className="settings-radio-group">
              <label>
                <input
                  type="radio"
                  name="global-update-opt"
                  checked={applyMode === 'new'}
                  onChange={() => setApplyMode('new')}
                />
                <span>Áp dụng template cho các service mới</span>
              </label>
              <label>
                <input
                  type="radio"
                  name="global-update-opt"
                  checked={applyMode === 'bulk'}
                  onChange={() => setApplyMode('bulk')}
                />
                <span>Cập nhật hàng loạt cho tất cả service</span>
              </label>
            </div>
            <button type="button" className="settings-save-button" onClick={handleSave} disabled={isSaving}>
              <span className="material-symbols-outlined">save</span>
              {isSaving ? 'Đang cập nhật...' : 'Cập nhật cấu hình Global'}
            </button>
          </div>
          {message ? <p className="settings-message">{message}</p> : null}
        </section>
      </div>
    </section>
  )
}
