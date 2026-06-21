import { DEFAULT_PAGE_SIZE } from './securityLogConstants'

export const EMPTY_VALUE = '—'

export function unwrapApiResponse(response) {
  if (response && typeof response === 'object' && response.status !== undefined && response.status !== 200) {
    throw new Error(response.message || 'Không thể tải nhật ký hệ thống.')
  }
  return response?.data ?? response
}

export function normalizeSecurityLogPage(response) {
  const data = unwrapApiResponse(response)
  const pageData = data?.data ?? data ?? {}
  const content = pageData.content ?? data?.content ?? []
  const size = Number(pageData.size ?? DEFAULT_PAGE_SIZE)
  const page = Number(pageData.page ?? pageData.number ?? 0)
  const totalElements = Number(pageData.totalElements ?? (content.length ?? 0))
  const totalPages = Number(pageData.totalPages ?? (size > 0 ? Math.ceil(totalElements / size) : 0))
  return {
    items: Array.isArray(content) ? content : [],
    pageInfo: {
      number: Number.isFinite(page) ? page : 0,
      size: Number.isFinite(size) && size > 0 ? size : DEFAULT_PAGE_SIZE,
      totalElements: Number.isFinite(totalElements) ? totalElements : 0,
      totalPages: Number.isFinite(totalPages) ? totalPages : 0,
      first: Boolean(pageData.first ?? (page <= 0)),
      last: Boolean(pageData.last ?? (totalPages === 0 || page + 1 >= totalPages)),
    },
  }
}

export function toIsoInstantFromInput(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toISOString()
}

export function formatValue(value) {
  if (value === undefined || value === null || value === '') return EMPTY_VALUE
  return String(value)
}

export function formatTimestamp(value) {
  if (!value) return EMPTY_VALUE
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return date.toLocaleString('vi-VN')
}

export function formatDuration(value) {
  if (value === undefined || value === null || value === '') return EMPTY_VALUE
  return `${Number(value).toLocaleString('vi-VN')} ms`
}

export function formatBytes(value) {
  if (value === undefined || value === null || value === '') return EMPTY_VALUE
  const number = Number(value)
  if (!Number.isFinite(number)) return String(value)
  if (number < 1024) return `${number} B`
  if (number < 1024 * 1024) return `${(number / 1024).toFixed(1)} KB`
  return `${(number / (1024 * 1024)).toFixed(1)} MB`
}

export function displayTarget(log) {
  return log?.path || log?.targetUrl || log?.topic || EMPTY_VALUE
}

export function maskClientKey(value) {
  if (!value) return EMPTY_VALUE
  if (value.length <= 8) return '••••'
  return `${value.slice(0, 4)}••••${value.slice(-4)}`
}

export function chipClass(value, type) {
  const normalized = String(value || '').toLowerCase()
  if (type === 'severity') return `security-log-chip security-log-chip--severity-${normalized || 'unknown'}`
  if (['success', 'ok', '200'].some((item) => normalized.includes(item))) return 'security-log-chip security-log-chip--success'
  if (['failed', 'error', 'denied', 'timeout', '5'].some((item) => normalized.includes(item))) return 'security-log-chip security-log-chip--danger'
  return 'security-log-chip'
}
