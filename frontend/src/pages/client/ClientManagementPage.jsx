import { memo, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { createClient, getClients, searchInbounds } from '../../services/clients'
import './ClientManagementPage.css'

const CLIENT_STATUSES = ['ACTIVE', 'INACTIVE', 'REVOKED']
const CREATE_STATUSES = ['ACTIVE', 'INACTIVE']
const AUTH_TYPES = ['API_KEY', 'HMAC_SIGNATURE']
const AUTH_ALGORITHMS = ['HMAC_SHA256', 'HMAC_SHA512']
const DEFAULT_PAGE_SIZE = 20

const emptyAuthConfig = () => ({
  inboundEndpointName: '',
  inboundEndpointId: '',
  type: 'API_KEY',
  algorithm: 'HMAC_SHA256',
  expiresAt: '',
})

const emptyForm = () => ({
  name: '',
  description: '',
  contactEmail: '',
  status: 'ACTIVE',
  authConfigs: [emptyAuthConfig()],
})

function unwrapResponse(response) {
  if (response && typeof response === 'object' && response.status !== undefined && response.status !== 200) {
    throw new Error(response.message || 'API request failed')
  }
  return response?.data ?? response
}

function normalizePagePayload(response) {
  const data = unwrapResponse(response)
  const pageData = data?.data ?? data
  const items = pageData?.content || pageData?.items || pageData?.data || (Array.isArray(pageData) ? pageData : [])
  const size = Number(pageData?.size ?? pageData?.pageSize ?? DEFAULT_PAGE_SIZE)
  const number = Number(pageData?.number ?? pageData?.page ?? 0)
  const totalElements = Number(pageData?.totalElements ?? pageData?.total ?? items.length)
  const totalPages = Number(pageData?.totalPages ?? (size > 0 ? Math.ceil(totalElements / size) : 1))

  return {
    items: Array.isArray(items) ? items : [],
    pageInfo: {
      number: Number.isFinite(number) ? number : 0,
      size: Number.isFinite(size) && size > 0 ? size : DEFAULT_PAGE_SIZE,
      totalElements: Number.isFinite(totalElements) ? totalElements : 0,
      totalPages: Number.isFinite(totalPages) && totalPages > 0 ? totalPages : 1,
      first: Boolean(pageData?.first ?? number <= 0),
      last: Boolean(pageData?.last ?? number + 1 >= totalPages),
    },
  }
}

function normalizeClient(client) {
  return {
    id: client?.id ?? client?.clientId ?? '',
    clientCode: client?.clientCode ?? client?.code ?? client?.clientKey ?? '',
    name: client?.name ?? '',
    description: client?.description ?? '',
    contactEmail: client?.contactEmail ?? '',
    status: client?.status || 'UNKNOWN',
    createdAt: client?.createdAt ?? '',
    updatedAt: client?.updatedAt ?? '',
  }
}

function formatDate(value) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return date.toLocaleDateString('vi-VN')
}

function statusClass(status) {
  if (status === 'ACTIVE') return 'client-status client-status--active'
  if (status === 'REVOKED') return 'client-status client-status--revoked'
  if (status === 'INACTIVE') return 'client-status client-status--inactive'
  return 'client-status'
}

function validateEmail(value) {
  if (!value) return true
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)
}

function toDateTime(value) {
  return value ? `${value}T23:59:59` : undefined
}

function buildCreateBody(form) {
  const name = form.name.trim()
  const contactEmail = form.contactEmail.trim()

  if (!name) throw new Error('Tên client là bắt buộc.')
  if (contactEmail && !validateEmail(contactEmail)) throw new Error('Email liên hệ không hợp lệ.')
  if (!CREATE_STATUSES.includes(form.status)) throw new Error('Trạng thái tạo client không hợp lệ.')
  if (form.authConfigs.some((config) => config.inboundEndpointName.trim() && !config.inboundEndpointId.trim())) {
    throw new Error('Vui lòng chọn inbound từ danh sách gợi ý để tự điền Endpoint ID.')
  }

  const authConfigs = form.authConfigs
    .filter((config) => config.inboundEndpointId.trim())
    .map((config) => {
      if (!AUTH_TYPES.includes(config.type)) throw new Error('Loại auth config không hợp lệ.')
      const item = {
        inboundEndpointId: config.inboundEndpointId.trim(),
        type: config.type,
      }
      const expiresAt = toDateTime(config.expiresAt)
      if (expiresAt) item.expiresAt = expiresAt
      if (config.type === 'HMAC_SIGNATURE') item.algorithm = config.algorithm || 'HMAC_SHA256'
      return item
    })

  const body = {
    name,
    description: form.description.trim(),
    contactEmail,
    status: form.status,
  }

  if (authConfigs.length > 0) body.authConfigs = authConfigs
  return body
}

function ErrorNotice({ message, onRetry }) {
  if (!message) return null
  return (
    <div className="client-error" role="alert">
      <span>{message}</span>
      {onRetry ? <button type="button" onClick={onRetry}>Thử lại</button> : null}
    </div>
  )
}

export default function ClientManagementPage() {
  const [clients, setClients] = useState([])
  const [filters, setFilters] = useState({ keyword: '', status: '' })
  const [draftFilters, setDraftFilters] = useState({ keyword: '', status: '' })
  const [pageInfo, setPageInfo] = useState({ number: 0, size: DEFAULT_PAGE_SIZE, totalElements: 0, totalPages: 1, first: true, last: true })
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [isModalOpen, setIsModalOpen] = useState(false)
  const [form, setForm] = useState(emptyForm)
  const [formError, setFormError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [createdCredential, setCreatedCredential] = useState(null)
  const [inboundSuggestions, setInboundSuggestions] = useState({})
  const inboundSearchRequestRef = useRef(0)

  const loadClients = useCallback(async ({ page = pageInfo.number, size = pageInfo.size, nextFilters = filters } = {}) => {
    setIsLoading(true)
    setError('')
    try {
      const response = await getClients({ ...nextFilters, page, size })
      const payload = normalizePagePayload(response)
      setClients(payload.items.map(normalizeClient))
      setPageInfo(payload.pageInfo)
    } catch (requestError) {
      setError(requestError.message || 'Không thể tải danh sách client.')
    } finally {
      setIsLoading(false)
    }
  }, [filters, pageInfo.number, pageInfo.size])

  useEffect(() => {
    let mounted = true
    Promise.resolve()
      .then(async () => {
        if (!mounted) return
        await loadClients({ page: 0, size: DEFAULT_PAGE_SIZE })
      })
      .catch((requestError) => {
        if (mounted) {
          setError(requestError.message || 'Không thể tải danh sách client.')
          setIsLoading(false)
        }
      })

    return () => {
      mounted = false
    }
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (!message) return undefined
    const timeoutId = window.setTimeout(() => setMessage(''), 3000)
    return () => window.clearTimeout(timeoutId)
  }, [message])

  const stats = useMemo(() => ({
    active: clients.filter((client) => client.status === 'ACTIVE').length,
    inactive: clients.filter((client) => client.status === 'INACTIVE').length,
    revoked: clients.filter((client) => client.status === 'REVOKED').length,
  }), [clients])

  const updateForm = useCallback((field, value) => {
    setForm((current) => ({ ...current, [field]: value }))
  }, [])

  const updateAuthConfig = useCallback((index, field, value) => {
    setForm((current) => ({
      ...current,
      authConfigs: current.authConfigs.map((config, configIndex) => {
        if (configIndex !== index) return config
        const nextConfig = { ...config, [field]: value }
        if (field === 'type' && value === 'API_KEY') nextConfig.algorithm = 'HMAC_SHA256'
        return nextConfig
      }),
    }))
  }, [])

  const updateInboundName = useCallback(async (index, value) => {
    setForm((current) => ({
      ...current,
      authConfigs: current.authConfigs.map((config, configIndex) => (
        configIndex === index ? { ...config, inboundEndpointName: value, inboundEndpointId: '' } : config
      )),
    }))

    const keyword = value.trim()
    const requestId = inboundSearchRequestRef.current + 1
    inboundSearchRequestRef.current = requestId
    if (!keyword) {
      setInboundSuggestions((current) => ({ ...current, [index]: [] }))
      return
    }

    try {
      const response = await searchInbounds({ name: keyword, size: 10 })
      const items = unwrapResponse(response)
      if (inboundSearchRequestRef.current !== requestId) return
      setInboundSuggestions((current) => ({ ...current, [index]: Array.isArray(items) ? items : [] }))
    } catch {
      if (inboundSearchRequestRef.current !== requestId) return
      setInboundSuggestions((current) => ({ ...current, [index]: [] }))
    }
  }, [])

  const selectInbound = useCallback((index, inbound) => {
    setForm((current) => ({
      ...current,
      authConfigs: current.authConfigs.map((config, configIndex) => (
        configIndex === index
          ? { ...config, inboundEndpointName: inbound?.name || '', inboundEndpointId: inbound?.id || '' }
          : config
      )),
    }))
    setInboundSuggestions((current) => ({ ...current, [index]: [] }))
  }, [])

  const addAuthConfig = useCallback(() => {
    setForm((current) => ({ ...current, authConfigs: [...current.authConfigs, emptyAuthConfig()] }))
  }, [])

  const removeAuthConfig = useCallback((index) => {
    setForm((current) => ({ ...current, authConfigs: current.authConfigs.filter((_, configIndex) => configIndex !== index) }))
    setInboundSuggestions((current) => Object.entries(current).reduce((nextSuggestions, [key, value]) => {
      const suggestionIndex = Number(key)
      if (suggestionIndex < index) nextSuggestions[suggestionIndex] = value
      if (suggestionIndex > index) nextSuggestions[suggestionIndex - 1] = value
      return nextSuggestions
    }, {}))
  }, [])

  const openModal = useCallback(() => {
    setForm(emptyForm())
    setFormError('')
    setCreatedCredential(null)
    setInboundSuggestions({})
    setIsModalOpen(true)
  }, [])

  const closeModal = useCallback(() => {
    if (!isSubmitting) setIsModalOpen(false)
  }, [isSubmitting])

  async function submitFilters(event) {
    event.preventDefault()
    setFilters(draftFilters)
    await loadClients({ page: 0, size: pageInfo.size, nextFilters: draftFilters })
  }

  const changePage = useCallback(async (nextPage) => {
    if (nextPage < 0 || nextPage >= pageInfo.totalPages || isLoading) return
    await loadClients({ page: nextPage, size: pageInfo.size })
  }, [isLoading, loadClients, pageInfo.size, pageInfo.totalPages])

  const changeSize = useCallback(async (event) => {
    const nextSize = Number(event.target.value)
    await loadClients({ page: 0, size: nextSize })
  }, [loadClients])

  async function handleCreateClient(event) {
    event.preventDefault()
    setFormError('')
    setMessage('')
    setCreatedCredential(null)
    setIsSubmitting(true)

    try {
      const response = await createClient(buildCreateBody(form))
      const data = unwrapResponse(response)
      setCreatedCredential(data?.credential || data?.credentials || null)
      setMessage(`Đã tạo client ${data?.clientCode || form.name.trim()} thành công.`)
      await loadClients({ page: 0, size: pageInfo.size })
      setForm(emptyForm())
      if (!data?.credential && !data?.credentials) setIsModalOpen(false)
    } catch (submitError) {
      setFormError(submitError.message || 'Tạo client thất bại.')
    } finally {
      setIsSubmitting(false)
    }
  }

  const startItem = clients.length > 0 ? pageInfo.number * pageInfo.size + 1 : 0
  const endItem = pageInfo.number * pageInfo.size + clients.length

  return (
    <section className="client-page">
      <div className="client-container">
        <header className="client-page-header">
          <div>
            <h2>Danh sách Client</h2>
            <p>Quản lý và giám sát các đơn vị kết nối với hệ thống.</p>
          </div>
        </header>

        <form className="client-filter-card" onSubmit={submitFilters}>
          <label>
            <span>Từ khóa</span>
            <input
              type="text"
              value={draftFilters.keyword}
              onChange={(event) => setDraftFilters((current) => ({ ...current, keyword: event.target.value }))}
              placeholder="Tìm theo tên hoặc mã"
            />
          </label>
          <label>
            <span>Trạng thái</span>
            <select value={draftFilters.status} onChange={(event) => setDraftFilters((current) => ({ ...current, status: event.target.value }))}>
              <option value="">Tất cả trạng thái</option>
              {CLIENT_STATUSES.map((status) => <option key={status} value={status}>{status}</option>)}
            </select>
          </label>
          <div className="client-filter-actions">
            <button type="submit" className="client-primary-button" disabled={isLoading}>
              <span className="material-symbols-outlined">search</span>
              Tìm kiếm
            </button>
            <button type="button" className="client-secondary-button" onClick={openModal}>
              <span className="material-symbols-outlined">add</span>
              Thêm mới Client
            </button>
          </div>
        </form>

        <ErrorNotice message={error} onRetry={() => loadClients()} />
        {message ? <p className="client-message">{message}</p> : null}

        <section className="client-table-card">
          <ClientTable
            clients={clients}
            isLoading={isLoading}
            pageInfo={pageInfo}
            startItem={startItem}
            endItem={endItem}
            onChangePage={changePage}
            onChangeSize={changeSize}
          />
        </section>

        <div className="client-stats-grid" aria-label="Thống kê theo trang hiện tại">
          <StatCard icon="check_circle" label="Đang hoạt động" value={stats.active} variant="green" />
          <StatCard icon="pause_circle" label="Tạm ngưng" value={stats.inactive} variant="gray" />
          <StatCard icon="cancel" label="Đã thu hồi" value={stats.revoked} variant="red" />
        </div>
      </div>

      {isModalOpen ? (
        <ClientCreateModal
          form={form}
          error={formError}
          credential={createdCredential}
          isSubmitting={isSubmitting}
          onClose={closeModal}
          onSubmit={handleCreateClient}
          onFormChange={updateForm}
          onAuthConfigChange={updateAuthConfig}
          onInboundNameChange={updateInboundName}
          onInboundSelect={selectInbound}
          inboundSuggestions={inboundSuggestions}
          onAddAuthConfig={addAuthConfig}
          onRemoveAuthConfig={removeAuthConfig}
        />
      ) : null}
    </section>
  )
}

const ClientTable = memo(function ClientTable({ clients, isLoading, pageInfo, startItem, endItem, onChangePage, onChangeSize }) {
  return (
    <>
      <div className="client-table-wrap">
        <table className="client-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Mã client</th>
              <th>Tên client</th>
              <th>Mô tả</th>
              <th>Trạng thái</th>
              <th>Ngày tạo</th>
              <th>Cập nhật</th>
              <th>Thao tác</th>
            </tr>
          </thead>
          <tbody>
            {clients.map((client) => (
              <tr key={client.id || client.clientCode}>
                <td><code>{client.id || '—'}</code></td>
                <td><strong className="client-code">{client.clientCode || '—'}</strong></td>
                <td>{client.name || '—'}</td>
                <td className="client-muted-cell">{client.description || '—'}</td>
                <td><span className={statusClass(client.status)}>{client.status}</span></td>
                <td>{formatDate(client.createdAt)}</td>
                <td>{formatDate(client.updatedAt)}</td>
                <td>
                  <div className="client-row-actions">
                    <button type="button" title="Xem chi tiết" disabled>
                      <span className="material-symbols-outlined">visibility</span>
                    </button>
                    <button type="button" title="Cập nhật trạng thái" disabled>
                      <span className="material-symbols-outlined">power_settings_new</span>
                    </button>
                    <button type="button" title="Access rule" disabled>
                      <span className="material-symbols-outlined">security</span>
                    </button>
                  </div>
                </td>
              </tr>
            ))}
            {isLoading ? (
              <tr><td colSpan="8" className="client-empty-cell">Đang tải danh sách client...</td></tr>
            ) : null}
            {!isLoading && clients.length === 0 ? (
              <tr><td colSpan="8" className="client-empty-cell">Không có client phù hợp.</td></tr>
            ) : null}
          </tbody>
        </table>
      </div>
      <div className="client-pagination">
        <div className="client-pagination-summary">
          <span>Hiển thị <strong>{startItem}-{endItem}</strong> / <strong>{pageInfo.totalElements}</strong> Client</span>
          <label>
            Kích thước:
            <select value={pageInfo.size} onChange={onChangeSize} disabled={isLoading}>
              {[10, 20, 50].map((size) => <option key={size} value={size}>{size}</option>)}
            </select>
          </label>
        </div>
        <div className="client-pagination-buttons">
          <button type="button" onClick={() => onChangePage(pageInfo.number - 1)} disabled={pageInfo.first || isLoading}>Trước</button>
          <button type="button" className="active">{pageInfo.number + 1}</button>
          <button type="button" onClick={() => onChangePage(pageInfo.number + 1)} disabled={pageInfo.last || isLoading}>{pageInfo.number + 2}</button>
          <button type="button" onClick={() => onChangePage(pageInfo.number + 1)} disabled={pageInfo.last || isLoading}>Tiếp</button>
        </div>
      </div>
    </>
  )
})

function StatCard({ icon, label, value, variant }) {
  return (
    <div className="client-stat-card">
      <div className={`client-stat-icon client-stat-icon--${variant}`}>
        <span className="material-symbols-outlined client-icon-fill">{icon}</span>
      </div>
      <div>
        <p>{label}</p>
        <strong>{value}</strong>
      </div>
    </div>
  )
}

const ClientCreateModal = memo(function ClientCreateModal({ form, error, credential, isSubmitting, onClose, onSubmit, onFormChange, onAuthConfigChange, onInboundNameChange, onInboundSelect, inboundSuggestions, onAddAuthConfig, onRemoveAuthConfig }) {
  return (
    <div className="client-modal" role="dialog" aria-modal="true" aria-labelledby="client-modal-title">
      <button type="button" className="client-modal__backdrop" aria-label="Đóng modal" onClick={onClose} />
      <form className="client-modal__panel" onSubmit={onSubmit}>
        <header className="client-modal__header">
          <h3 id="client-modal-title">Đăng ký Client mới</h3>
          <button type="button" onClick={onClose} disabled={isSubmitting} aria-label="Đóng">
            <span className="material-symbols-outlined">close</span>
          </button>
        </header>

        <div className="client-modal__body">
          <ErrorNotice message={error} />
          <section className="client-form-section">
            <h4>Thông tin cơ bản</h4>
            <div className="client-form-grid">
              <TextField label="Tên client" value={form.name} onChange={(value) => onFormChange('name', value)} required />
              <TextField label="Email liên hệ" type="email" value={form.contactEmail} onChange={(value) => onFormChange('contactEmail', value)} />
              <label className="client-field">
                <span>Trạng thái</span>
                <select value={form.status} onChange={(event) => onFormChange('status', event.target.value)}>
                  {CREATE_STATUSES.map((status) => <option key={status}>{status}</option>)}
                </select>
              </label>
              <label className="client-field client-field--wide">
                <span>Mô tả</span>
                <textarea rows="2" value={form.description} onChange={(event) => onFormChange('description', event.target.value)} />
              </label>
            </div>
          </section>

          <section className="client-form-section">
            <div className="client-form-section__title-row">
              <h4>Cấu hình xác thực (Auth Configs)</h4>
              <button type="button" onClick={onAddAuthConfig}>
                <span className="material-symbols-outlined">add_circle</span>
                Thêm cấu hình
              </button>
            </div>
            <div className="client-auth-list">
              {form.authConfigs.map((config, index) => (
                <div className="client-auth-row" key={`${index}-${config.type}`}>
                  <InboundSearchField
                    value={config.inboundEndpointName}
                    suggestions={inboundSuggestions[index] || []}
                    onChange={(value) => onInboundNameChange(index, value)}
                    onSelect={(inbound) => onInboundSelect(index, inbound)}
                  />
                  <TextField label="Endpoint ID" value={config.inboundEndpointId} onChange={(value) => onAuthConfigChange(index, 'inboundEndpointId', value)} placeholder="endpoint-id" />
                  <label className="client-field">
                    <span>Type</span>
                    <select value={config.type} onChange={(event) => onAuthConfigChange(index, 'type', event.target.value)}>
                      {AUTH_TYPES.map((type) => <option key={type}>{type}</option>)}
                    </select>
                  </label>
                  <label className="client-field">
                    <span>Algorithm</span>
                    <select value={config.algorithm} onChange={(event) => onAuthConfigChange(index, 'algorithm', event.target.value)} disabled={config.type === 'API_KEY'}>
                      {AUTH_ALGORITHMS.map((algorithm) => <option key={algorithm}>{algorithm}</option>)}
                    </select>
                  </label>
                  <TextField label="Expires at" type="date" value={config.expiresAt} onChange={(value) => onAuthConfigChange(index, 'expiresAt', value)} />
                  <button type="button" className="client-auth-remove" onClick={() => onRemoveAuthConfig(index)} disabled={form.authConfigs.length === 1} aria-label="Xóa auth config">
                    <span className="material-symbols-outlined">delete</span>
                  </button>
                </div>
              ))}
            </div>
            <p className="client-form-hint">* Credential sẽ được backend khởi tạo tự động sau khi lưu và chỉ hiển thị một lần nếu API trả về.</p>
          </section>

          {credential ? <CredentialBox credential={credential} /> : null}
        </div>

        <footer className="client-modal__footer">
          <button type="button" className="client-cancel-button" onClick={onClose} disabled={isSubmitting}>Hủy bỏ</button>
          {credential ? (
            <button type="button" className="client-primary-button" onClick={onClose}>Hoàn tất đăng ký</button>
          ) : (
            <button type="submit" className="client-primary-button" disabled={isSubmitting}>{isSubmitting ? 'Đang đăng ký...' : 'Xác nhận đăng ký'}</button>
          )}
        </footer>
      </form>
    </div>
  )
})

function InboundSearchField({ value, suggestions, onChange, onSelect }) {
  return (
    <div className="client-field client-inbound-search">
      <span>Tên inbound</span>
      <input type="text" value={value || ''} onChange={(event) => onChange(event.target.value)} placeholder="Nhập tên inbound" autoComplete="off" />
      {suggestions.length > 0 ? (
        <div className="client-inbound-suggestions">
          {suggestions.map((inbound) => (
            <button type="button" key={inbound.id} onClick={() => onSelect(inbound)}>
              <strong>{inbound.name || inbound.id}</strong>
              <span>{inbound.path || inbound.topic || inbound.serviceId || inbound.id}</span>
            </button>
          ))}
        </div>
      ) : null}
    </div>
  )
}

function TextField({ label, value, onChange, type = 'text', placeholder = '', required = false }) {
  return (
    <label className="client-field">
      <span>{label}</span>
      <input type={type} value={value} onChange={(event) => onChange(event.target.value)} placeholder={placeholder} required={required} />
    </label>
  )
}

function CredentialBox({ credential }) {
  const entries = Array.isArray(credential)
    ? credential.flatMap((item, index) => Object.entries(item || {}).map(([key, value]) => [`${index + 1}.${key}`, value]))
    : Object.entries(credential || {})

  if (entries.length === 0) return null
  return (
    <section className="client-credential-box">
      <h4>Credential trả về một lần</h4>
      {entries.map(([key, value]) => (
        <p key={key}><span>{key}</span><code>{String(value)}</code></p>
      ))}
    </section>
  )
}
