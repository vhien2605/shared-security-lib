import { memo, useCallback, useEffect, useState } from 'react'
import {
  createAccessPermission,
  createAccessRule,
  deleteAccessPermission,
  deleteAccessRule,
  listAccessPermissions,
  listAllAccessRules,
  normalizePagePayload,
  unwrapResponse,
  updateAccessPermission,
  updateAccessRule,
} from '../../services/accessControl'
import {
  ACCESS_CONTROL_DEFAULT_PAGE_SIZE,
  RULE_TYPES,
  RULE_VALUE_TYPES,
  VALUE_TYPE_OPTIONS,
} from '../../types/accessControl'
import './PermissionControlPage.css'

const EMPTY_PAGE_INFO = {
  number: 0,
  size: ACCESS_CONTROL_DEFAULT_PAGE_SIZE,
  totalElements: 0,
  totalPages: 1,
  first: true,
  last: true,
}

function emptyRuleForm(type) {
  return {
    type,
    inboundEndpointId: '',
    valueType: RULE_VALUE_TYPES.IP,
    value: '',
    temporary: false,
    expiresAt: '',
    reason: '',
    enable: true,
  }
}

function emptyPermissionForm() {
  return {
    clientId: '',
    inboundEndpointId: '',
    enable: true,
  }
}

function formatDateTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return new Intl.DateTimeFormat('vi-VN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

function getErrorMessage(error, fallback) {
  return error?.message || fallback
}

function validateRuleForm(form) {
  if (!form.inboundEndpointId.trim()) throw new Error('Inbound Endpoint ID là bắt buộc.')
  if (!Object.values(RULE_TYPES).includes(form.type)) throw new Error('Loại rule không hợp lệ.')
  if (!Object.values(RULE_VALUE_TYPES).includes(form.valueType)) throw new Error('Value Type không hợp lệ.')
  if (!form.value.trim()) throw new Error('Value là bắt buộc.')
  if (form.temporary && !form.expiresAt) throw new Error('Ngày hết hạn là bắt buộc khi rule tạm thời.')
}

function validatePermissionForm(form) {
  if (!form.clientId.trim()) throw new Error('Client ID là bắt buộc.')
  if (!form.inboundEndpointId.trim()) throw new Error('Inbound Endpoint ID là bắt buộc.')
}

function ToggleSwitch({ checked, disabled, onChange, label }) {
  return (
    <button
      type="button"
      className={checked ? 'permission-toggle permission-toggle--on' : 'permission-toggle'}
      role="switch"
      aria-checked={checked}
      aria-label={label}
      disabled={disabled}
      onClick={onChange}
    >
      <span />
    </button>
  )
}

function SearchInput({ value, placeholder, onChange }) {
  return (
    <label className="permission-search">
      <span className="material-symbols-outlined" aria-hidden="true">search</span>
      <input value={value} placeholder={placeholder} onChange={(event) => onChange(event.target.value)} />
    </label>
  )
}

function ValueTypeFilter({ value, onChange }) {
  return (
    <label className="permission-value-type-filter">
      <span className="material-symbols-outlined" aria-hidden="true">filter_alt</span>
      <select value={value} onChange={(event) => onChange(event.target.value)}>
        {VALUE_TYPE_OPTIONS.map((opt) => (
          <option key={opt.value} value={opt.value}>{opt.label}</option>
        ))}
      </select>
    </label>
  )
}

function Pagination({ pageInfo, onPageChange }) {
  return (
    <div className="permission-pagination" aria-label="Phân trang">
      <span>
        Trang {pageInfo.number + 1}/{pageInfo.totalPages} · {pageInfo.totalElements} bản ghi
      </span>
      <div>
        <button type="button" disabled={pageInfo.first} onClick={() => onPageChange(pageInfo.number - 1)}>
          Trước
        </button>
        <button type="button" disabled={pageInfo.last} onClick={() => onPageChange(pageInfo.number + 1)}>
          Sau
        </button>
      </div>
    </div>
  )
}

const AccessRuleSection = memo(function AccessRuleSection({
  type,
  title,
  description,
  icon,
  valueType,
  keyword,
  rows,
  pageInfo,
  isLoading,
  error,
  onValueTypeChange,
  onKeywordChange,
  onPageChange,
  onOpenCreate,
  onToggle,
  onDelete,
}) {
  const emptyText = isLoading ? 'Đang tải dữ liệu từ API...' : error || 'Không có dữ liệu phù hợp.'

  return (
    <section className="permission-section">
      <div className="permission-section__toolbar">
        <div className="permission-section__title-group">
          <h3 className={type === RULE_TYPES.BLACKLIST ? 'permission-section__title permission-section__title--danger' : 'permission-section__title'}>
            <span className="material-symbols-outlined" aria-hidden="true">{icon}</span>
            {title}
          </h3>
          <p>{description}</p>
        </div>
        <div className="permission-section__filters">
          <ValueTypeFilter value={valueType} onChange={onValueTypeChange} />
          <SearchInput value={keyword} placeholder="Tìm theo Service ID hoặc Endpoint ID..." onChange={onKeywordChange} />
        </div>
        <button type="button" className="permission-button permission-button--primary" onClick={onOpenCreate}>
          <span className="material-symbols-outlined" aria-hidden="true">add</span>
          Thêm mới {type === RULE_TYPES.BLACKLIST ? 'Blacklist' : 'Whitelist'}
        </button>
      </div>
      <div className="permission-table-wrap">
        <table className="permission-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Service ID</th>
              <th>Service Name</th>
              <th>Endpoint</th>
              <th>Value Type</th>
              <th>Value</th>
              <th>Temporary</th>
              <th>Expires At</th>
              <th>Reason</th>
              <th>Enable</th>
              <th className="permission-table__actions">Actions</th>
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 ? (
              <tr><td colSpan="11" className="permission-empty">{emptyText}</td></tr>
            ) : rows.map((rule) => (
              <tr key={rule.id}>
                <td className="permission-mono">#{rule.id}</td>
                <td className="permission-mono">{rule.serviceId || '-'}</td>
                <td>{rule.serviceName || '-'}</td>
                <td><code>{rule.inboundEndpointName ? `${rule.inboundEndpointName} (${rule.inboundEndpointId})` : rule.inboundEndpointId}</code></td>
                <td><span className="permission-chip">{rule.valueType}</span></td>
                <td>{rule.value}</td>
                <td className="permission-center">{rule.temporary ? 'Có' : 'Không'}</td>
                <td>{rule.temporary ? formatDateTime(rule.expiresAt) : 'Vĩnh viễn'}</td>
                <td className="permission-reason">{rule.reason || '-'}</td>
                <td className="permission-center">
                  <ToggleSwitch checked={Boolean(rule.enable)} disabled={isLoading} label={`Bật/tắt rule ${rule.id}`} onChange={() => onToggle(rule)} />
                </td>
                <td className="permission-table__actions">
                  <button type="button" className="permission-icon-button" aria-label={`Xóa rule ${rule.id}`} disabled={isLoading} onClick={() => onDelete(rule)}>
                    <span className="material-symbols-outlined" aria-hidden="true">delete</span>
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <Pagination pageInfo={pageInfo} onPageChange={onPageChange} />
    </section>
  )
})

const AccessPermissionSection = memo(function AccessPermissionSection({ keyword, rows, pageInfo, isLoading, error, onKeywordChange, onPageChange, onOpenCreate, onToggle, onDelete }) {
  const emptyText = isLoading ? 'Đang tải dữ liệu từ API...' : error || 'Không có dữ liệu phù hợp.'

  return (
    <section className="permission-section">
      <div className="permission-section__toolbar">
        <div className="permission-section__title-group">
          <h3 className="permission-section__title">
            <span className="material-symbols-outlined" aria-hidden="true">key</span>
            Quản lý quyền truy cập thông thường
          </h3>
          <p>Cấp phát quyền hạn chi tiết cho các Client và Endpoint cụ thể.</p>
        </div>
        <SearchInput value={keyword} placeholder="Tìm theo Service ID, Endpoint ID hoặc Client ID..." onChange={onKeywordChange} />
        <button type="button" className="permission-button permission-button--primary" onClick={onOpenCreate}>
          <span className="material-symbols-outlined" aria-hidden="true">add</span>
          Thêm mới Quyền truy cập
        </button>
      </div>
      <div className="permission-table-wrap">
        <table className="permission-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Service ID</th>
              <th>Service Name</th>
              <th>Client ID</th>
              <th>Client Key</th>
              <th>Inbound Endpoint ID</th>
              <th>Endpoint Name</th>
              <th>Endpoint Path</th>
              <th>Created At</th>
              <th>Updated At</th>
              <th>Enable</th>
              <th className="permission-table__actions">Actions</th>
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 ? (
              <tr><td colSpan="12" className="permission-empty">{emptyText}</td></tr>
            ) : rows.map((permission) => (
              <tr key={permission.id}>
                <td className="permission-mono">#{permission.id}</td>
                <td className="permission-mono">{permission.serviceId || '-'}</td>
                <td>{permission.serviceName || permission.clientName || '-'}</td>
                <td>{permission.clientId}</td>
                <td><code>{permission.clientCode || permission.clientKey || '-'}</code></td>
                <td><code>{permission.inboundEndpointId}</code></td>
                <td>{permission.endpointName || permission.inboundEndpointName || '-'}</td>
                <td><code>{permission.inboundEndpointPath || '-'}</code></td>
                <td>{formatDateTime(permission.createdAt)}</td>
                <td>{formatDateTime(permission.updatedAt)}</td>
                <td className="permission-center">
                  <ToggleSwitch checked={Boolean(permission.enable)} disabled={isLoading} label={`Bật/tắt quyền ${permission.id}`} onChange={() => onToggle(permission)} />
                </td>
                <td className="permission-table__actions">
                  <button type="button" className="permission-icon-button" aria-label={`Xóa quyền ${permission.id}`} disabled={isLoading} onClick={() => onDelete(permission)}>
                    <span className="material-symbols-outlined" aria-hidden="true">delete</span>
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <Pagination pageInfo={pageInfo} onPageChange={onPageChange} />
    </section>
  )
})

function RuleFormModal({ form, formError, isSaving, onChange, onClose, onSubmit }) {
  const isBlacklist = form.type === RULE_TYPES.BLACKLIST
  return (
    <form className="permission-modal" role="dialog" aria-modal="true" aria-labelledby="permission-rule-modal-title" onSubmit={onSubmit}>
      <div className="permission-modal__header">
        <h3 id="permission-rule-modal-title">Thêm mới {isBlacklist ? 'Blacklist' : 'Whitelist'}</h3>
        <button type="button" className="permission-icon-button" aria-label="Đóng modal" disabled={isSaving} onClick={onClose}>
          <span className="material-symbols-outlined" aria-hidden="true">close</span>
        </button>
      </div>
      <div className="permission-modal__body">
        {formError ? <div className="permission-form-error" role="alert">{formError}</div> : null}
        <label>Inbound Endpoint ID<input value={form.inboundEndpointId} onChange={(event) => onChange('inboundEndpointId', event.target.value)} placeholder="VD: EP_AUTH_TOKEN_GEN" /></label>
        <div className="permission-form-grid">
          <label>Value Type<select value={form.valueType} onChange={(event) => onChange('valueType', event.target.value)}><option value={RULE_VALUE_TYPES.IP}>IP</option><option value={RULE_VALUE_TYPES.CIDR}>CIDR</option><option value={RULE_VALUE_TYPES.CLIENT_KEY}>CLIENT_KEY</option><option value={RULE_VALUE_TYPES.HEADER}>HEADER</option></select></label>
          <label>Value<input value={form.value} onChange={(event) => onChange('value', event.target.value)} placeholder="VD: 192.168.1.1" /></label>
        </div>
        <label className="permission-checkbox"><input type="checkbox" checked={form.temporary} onChange={(event) => onChange('temporary', event.target.checked)} /> Tạm thời</label>
        {form.temporary ? <label>Ngày hết hạn<input type="datetime-local" value={form.expiresAt} onChange={(event) => onChange('expiresAt', event.target.value)} /></label> : null}
        <label className="permission-checkbox"><input type="checkbox" checked={form.enable} onChange={(event) => onChange('enable', event.target.checked)} /> Bật rule sau khi tạo</label>
        <label>Lý do<textarea rows="3" value={form.reason} onChange={(event) => onChange('reason', event.target.value)} placeholder={isBlacklist ? 'Nhập lý do chặn...' : 'Nhập lý do cho phép...'} /></label>
      </div>
      <div className="permission-modal__footer">
        <button type="button" className="permission-button" disabled={isSaving} onClick={onClose}>Hủy bỏ</button>
        <button type="submit" className="permission-button permission-button--primary" disabled={isSaving}>{isSaving ? 'Đang lưu...' : `Thêm vào ${isBlacklist ? 'Blacklist' : 'Whitelist'}`}</button>
      </div>
    </form>
  )
}

function PermissionFormModal({ form, formError, isSaving, onChange, onClose, onSubmit }) {
  return (
    <form className="permission-modal" role="dialog" aria-modal="true" aria-labelledby="permission-access-modal-title" onSubmit={onSubmit}>
      <div className="permission-modal__header">
        <h3 id="permission-access-modal-title">Thêm mới Quyền truy cập</h3>
        <button type="button" className="permission-icon-button" aria-label="Đóng modal" disabled={isSaving} onClick={onClose}>
          <span className="material-symbols-outlined" aria-hidden="true">close</span>
        </button>
      </div>
      <div className="permission-modal__body">
        {formError ? <div className="permission-form-error" role="alert">{formError}</div> : null}
        <label>Client ID<input value={form.clientId} onChange={(event) => onChange('clientId', event.target.value)} placeholder="VD: CLIENT_APP_MOBILE_IOS" /></label>
        <label>Inbound Endpoint ID<input value={form.inboundEndpointId} onChange={(event) => onChange('inboundEndpointId', event.target.value)} placeholder="VD: EP_AUTH_TOKEN_GEN" /></label>
        <label className="permission-checkbox"><input type="checkbox" checked={form.enable} onChange={(event) => onChange('enable', event.target.checked)} /> Trạng thái hoạt động</label>
      </div>
      <div className="permission-modal__footer">
        <button type="button" className="permission-button" disabled={isSaving} onClick={onClose}>Hủy bỏ</button>
        <button type="submit" className="permission-button permission-button--primary" disabled={isSaving}>{isSaving ? 'Đang lưu...' : 'Cấp quyền truy cập'}</button>
      </div>
    </form>
  )
}

export default function PermissionControlPage() {
  const [rules, setRules] = useState({ blacklist: [], whitelist: [] })
  const [permissions, setPermissions] = useState([])
  const [pageInfo, setPageInfo] = useState({ blacklist: EMPTY_PAGE_INFO, whitelist: EMPTY_PAGE_INFO, permissions: EMPTY_PAGE_INFO })
  const [keywords, setKeywords] = useState({ blacklist: '', whitelist: '', permissions: '' })
  const [valueTypes, setValueTypes] = useState({ blacklist: '', whitelist: '' })
  const [pages, setPages] = useState({ blacklist: 0, whitelist: 0, permissions: 0 })
  const [loading, setLoading] = useState({ blacklist: false, whitelist: false, permissions: false })
  const [errors, setErrors] = useState({ blacklist: '', whitelist: '', permissions: '' })
  const [reloadKeys, setReloadKeys] = useState({ blacklist: 0, whitelist: 0, permissions: 0 })
  const [message, setMessage] = useState('')
  const [modal, setModal] = useState({ type: '', form: null, error: '' })
  const [isSaving, setIsSaving] = useState(false)

  useEffect(() => {
    if (!message) return undefined
    const timeoutId = window.setTimeout(() => setMessage(''), 3000)
    return () => window.clearTimeout(timeoutId)
  }, [message])

  useEffect(() => {
    let isActive = true
    setLoading((current) => ({ ...current, blacklist: true }))
    setErrors((current) => ({ ...current, blacklist: '' }))

    listAllAccessRules({
      type: RULE_TYPES.BLACKLIST,
      valueType: valueTypes.blacklist || undefined,
      keyword: keywords.blacklist || undefined,
      page: pages.blacklist,
      size: ACCESS_CONTROL_DEFAULT_PAGE_SIZE,
    })
      .then((response) => {
        if (!isActive) return
        const payload = normalizePagePayload(response)
        setRules((current) => ({ ...current, blacklist: payload.items }))
        setPageInfo((current) => ({ ...current, blacklist: payload.pageInfo }))
      })
      .catch((error) => {
        if (!isActive) return
        setRules((current) => ({ ...current, blacklist: [] }))
        setPageInfo((current) => ({ ...current, blacklist: EMPTY_PAGE_INFO }))
        setErrors((current) => ({ ...current, blacklist: getErrorMessage(error, 'Không thể tải Blacklist.') }))
      })
      .finally(() => {
        if (isActive) setLoading((current) => ({ ...current, blacklist: false }))
      })

    return () => { isActive = false }
  }, [keywords.blacklist, pages.blacklist, reloadKeys.blacklist, valueTypes.blacklist])

  useEffect(() => {
    let isActive = true
    setLoading((current) => ({ ...current, whitelist: true }))
    setErrors((current) => ({ ...current, whitelist: '' }))

    listAllAccessRules({
      type: RULE_TYPES.WHITELIST,
      valueType: valueTypes.whitelist || undefined,
      keyword: keywords.whitelist || undefined,
      page: pages.whitelist,
      size: ACCESS_CONTROL_DEFAULT_PAGE_SIZE,
    })
      .then((response) => {
        if (!isActive) return
        const payload = normalizePagePayload(response)
        setRules((current) => ({ ...current, whitelist: payload.items }))
        setPageInfo((current) => ({ ...current, whitelist: payload.pageInfo }))
      })
      .catch((error) => {
        if (!isActive) return
        setRules((current) => ({ ...current, whitelist: [] }))
        setPageInfo((current) => ({ ...current, whitelist: EMPTY_PAGE_INFO }))
        setErrors((current) => ({ ...current, whitelist: getErrorMessage(error, 'Không thể tải Whitelist.') }))
      })
      .finally(() => {
        if (isActive) setLoading((current) => ({ ...current, whitelist: false }))
      })

    return () => { isActive = false }
  }, [keywords.whitelist, pages.whitelist, reloadKeys.whitelist, valueTypes.whitelist])

  useEffect(() => {
    let isActive = true
    setLoading((current) => ({ ...current, permissions: true }))
    setErrors((current) => ({ ...current, permissions: '' }))

    listAccessPermissions({
      keyword: keywords.permissions || undefined,
      page: pages.permissions,
      size: ACCESS_CONTROL_DEFAULT_PAGE_SIZE,
    })
      .then((response) => {
        if (!isActive) return
        const payload = normalizePagePayload(response)
        setPermissions(payload.items)
        setPageInfo((current) => ({ ...current, permissions: payload.pageInfo }))
      })
      .catch((error) => {
        if (!isActive) return
        setPermissions([])
        setPageInfo((current) => ({ ...current, permissions: EMPTY_PAGE_INFO }))
        setErrors((current) => ({ ...current, permissions: getErrorMessage(error, 'Không thể tải quyền truy cập.') }))
      })
      .finally(() => {
        if (isActive) setLoading((current) => ({ ...current, permissions: false }))
      })

    return () => { isActive = false }
  }, [keywords.permissions, pages.permissions, reloadKeys.permissions])

  const reloadSection = useCallback((section) => {
    setReloadKeys((current) => ({ ...current, [section]: current[section] + 1 }))
  }, [])

  const updateKeyword = useCallback((section, value) => {
    setKeywords((current) => ({ ...current, [section]: value }))
    setPages((current) => ({ ...current, [section]: 0 }))
  }, [])

  const updateValueType = useCallback((section, value) => {
    setValueTypes((current) => ({ ...current, [section]: value }))
    setPages((current) => ({ ...current, [section]: 0 }))
  }, [])

  const openRuleModal = useCallback((type) => setModal({ type: 'rule', form: emptyRuleForm(type), error: '' }), [])
  const openPermissionModal = useCallback(() => setModal({ type: 'permission', form: emptyPermissionForm(), error: '' }), [])
  const closeModal = useCallback(() => {
    if (!isSaving) setModal({ type: '', form: null, error: '' })
  }, [isSaving])

  const updateModalForm = useCallback((field, value) => {
    setModal((current) => ({ ...current, form: { ...current.form, [field]: value }, error: '' }))
  }, [])

  const toggleRule = useCallback(async (rule) => {
    try {
      const response = await updateAccessRule(rule.id, !rule.enable)
      const updatedRule = unwrapResponse(response)
      const section = rule.type === RULE_TYPES.BLACKLIST ? 'blacklist' : 'whitelist'
      setRules((current) => ({
        ...current,
        [section]: current[section].map((item) => (item.id === rule.id ? { ...item, ...updatedRule } : item)),
      }))
      setMessage('Đã cập nhật trạng thái rule qua API.')
    } catch (error) {
      setMessage(getErrorMessage(error, 'Không thể cập nhật trạng thái rule.'))
    }
  }, [])

  const togglePermission = useCallback(async (permission) => {
    try {
      const response = await updateAccessPermission(permission.id, !permission.enable)
      const updatedPermission = unwrapResponse(response)
      setPermissions((current) => current.map((item) => (item.id === permission.id ? { ...item, ...updatedPermission } : item)))
      setMessage('Đã cập nhật trạng thái quyền truy cập qua API.')
    } catch (error) {
      setMessage(getErrorMessage(error, 'Không thể cập nhật trạng thái quyền truy cập.'))
    }
  }, [])

  const removeRule = useCallback(async (rule) => {
    if (!window.confirm('Xóa rule này qua API?')) return
    try {
      unwrapResponse(await deleteAccessRule(rule.id))
      const section = rule.type === RULE_TYPES.BLACKLIST ? 'blacklist' : 'whitelist'
      reloadSection(section)
      setMessage('Đã xóa rule qua API.')
    } catch (error) {
      setMessage(getErrorMessage(error, 'Không thể xóa rule.'))
    }
  }, [reloadSection])

  const removePermission = useCallback(async (permission) => {
    if (!window.confirm('Xóa quyền truy cập này qua API?')) return
    try {
      unwrapResponse(await deleteAccessPermission(permission.id))
      reloadSection('permissions')
      setMessage('Đã xóa quyền truy cập qua API.')
    } catch (error) {
      setMessage(getErrorMessage(error, 'Không thể xóa quyền truy cập.'))
    }
  }, [reloadSection])

  const submitRule = useCallback(async (event) => {
    event.preventDefault()
    if (isSaving) return
    try {
      validateRuleForm(modal.form)
      setIsSaving(true)
      const inboundEndpointId = modal.form.inboundEndpointId.trim()
      const body = {
        type: modal.form.type,
        valueType: modal.form.valueType,
        value: modal.form.value.trim(),
        temporary: Boolean(modal.form.temporary),
        enable: Boolean(modal.form.enable),
        expiresAt: modal.form.temporary ? modal.form.expiresAt : null,
        reason: modal.form.reason.trim() || null,
      }
      unwrapResponse(await createAccessRule(inboundEndpointId, body))
      const section = modal.form.type === RULE_TYPES.BLACKLIST ? 'blacklist' : 'whitelist'
      setPages((current) => ({ ...current, [section]: 0 }))
      reloadSection(section)
      setModal({ type: '', form: null, error: '' })
      setMessage(`Đã thêm ${modal.form.type === RULE_TYPES.BLACKLIST ? 'Blacklist' : 'Whitelist'} qua API.`)
    } catch (error) {
      setModal((current) => ({ ...current, error: getErrorMessage(error, 'Không thể tạo rule.') }))
    } finally {
      setIsSaving(false)
    }
  }, [isSaving, modal.form, reloadSection])

  const submitPermission = useCallback(async (event) => {
    event.preventDefault()
    if (isSaving) return
    try {
      validatePermissionForm(modal.form)
      setIsSaving(true)
      unwrapResponse(await createAccessPermission({
        clientId: modal.form.clientId.trim(),
        inboundEndpointId: modal.form.inboundEndpointId.trim(),
        enable: Boolean(modal.form.enable),
      }))
      setPages((current) => ({ ...current, permissions: 0 }))
      reloadSection('permissions')
      setModal({ type: '', form: null, error: '' })
      setMessage('Đã thêm quyền truy cập qua API.')
    } catch (error) {
      setModal((current) => ({ ...current, error: getErrorMessage(error, 'Không thể tạo quyền truy cập.') }))
    } finally {
      setIsSaving(false)
    }
  }, [isSaving, modal.form, reloadSection])

  return (
    <div className="permission-page">
      <header className="permission-page__header">
        <div>
          <h2>Quản lý quyền truy cập</h2>
          <p>Thiết lập và kiểm soát các quy tắc truy cập hệ thống Sentinel bằng API thật.</p>
        </div>
        <div className="permission-page__summary" aria-label="Thống kê quyền hạn">
          <span>{pageInfo.blacklist.totalElements} Blacklist</span>
          <span>{pageInfo.whitelist.totalElements} Whitelist</span>
          <span>{pageInfo.permissions.totalElements} Quyền thường</span>
        </div>
      </header>

      {message ? <div className="permission-message" role="status">{message}</div> : null}

      <AccessRuleSection
        type={RULE_TYPES.BLACKLIST}
        title="Quản lý Blacklist"
        description="Danh sách các thực thể bị từ chối truy cập vĩnh viễn hoặc tạm thời. Dữ liệu được tải từ API và có thể lọc theo Value Type hoặc tìm kiếm."
        icon="block"
        valueType={valueTypes.blacklist}
        keyword={keywords.blacklist}
        rows={rules.blacklist}
        pageInfo={pageInfo.blacklist}
        isLoading={loading.blacklist}
        error={errors.blacklist}
        onValueTypeChange={(value) => updateValueType('blacklist', value)}
        onKeywordChange={(value) => updateKeyword('blacklist', value)}
        onPageChange={(page) => setPages((current) => ({ ...current, blacklist: page }))}
        onOpenCreate={() => openRuleModal(RULE_TYPES.BLACKLIST)}
        onToggle={toggleRule}
        onDelete={removeRule}
      />

      <AccessRuleSection
        type={RULE_TYPES.WHITELIST}
        title="Quản lý Whitelist"
        description="Danh sách ưu tiên truy cập không bị giới hạn bởi các bộ lọc bảo mật thông thường. Dữ liệu được tải từ API và có thể lọc theo Value Type hoặc tìm kiếm."
        icon="verified_user"
        valueType={valueTypes.whitelist}
        keyword={keywords.whitelist}
        rows={rules.whitelist}
        pageInfo={pageInfo.whitelist}
        isLoading={loading.whitelist}
        error={errors.whitelist}
        onValueTypeChange={(value) => updateValueType('whitelist', value)}
        onKeywordChange={(value) => updateKeyword('whitelist', value)}
        onPageChange={(page) => setPages((current) => ({ ...current, whitelist: page }))}
        onOpenCreate={() => openRuleModal(RULE_TYPES.WHITELIST)}
        onToggle={toggleRule}
        onDelete={removeRule}
      />

      <AccessPermissionSection
        keyword={keywords.permissions}
        rows={permissions}
        pageInfo={pageInfo.permissions}
        isLoading={loading.permissions}
        error={errors.permissions}
        onKeywordChange={(value) => updateKeyword('permissions', value)}
        onPageChange={(page) => setPages((current) => ({ ...current, permissions: page }))}
        onOpenCreate={openPermissionModal}
        onToggle={togglePermission}
        onDelete={removePermission}
      />

      {modal.form ? (
        <div className="permission-modal-overlay" onMouseDown={(event) => { if (event.target === event.currentTarget) closeModal() }}>
          {modal.type === 'rule' ? (
            <RuleFormModal form={modal.form} formError={modal.error} isSaving={isSaving} onChange={updateModalForm} onClose={closeModal} onSubmit={submitRule} />
          ) : (
            <PermissionFormModal form={modal.form} formError={modal.error} isSaving={isSaving} onChange={updateModalForm} onClose={closeModal} onSubmit={submitPermission} />
          )}
        </div>
      ) : null}
    </div>
  )
}
