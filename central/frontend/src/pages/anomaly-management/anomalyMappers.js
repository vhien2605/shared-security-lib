import { DEFAULT_ANOMALY_PAGE_SIZE } from './anomalyConstants'

export const EMPTY_ANOMALY_VALUE = '—'

export function unwrapAnomalyResponse(response) {
  if (response && typeof response === 'object' && response.status !== undefined && response.status !== 200) {
    throw new Error(response.message || 'Không thể tải dữ liệu bất thường.')
  }
  return response?.data ?? response
}

export function normalizeAnomalyPage(response) {
  const data = unwrapAnomalyResponse(response)
  const content = Array.isArray(data?.content) ? data.content : []
  const size = Number(data?.size ?? DEFAULT_ANOMALY_PAGE_SIZE)
  const page = Number(data?.page ?? data?.number ?? 0)
  const totalElements = Number(data?.totalElements ?? content.length)
  const totalPages = Number(data?.totalPages ?? (size > 0 ? Math.ceil(totalElements / size) : 0))
  return {
    items: content,
    pageInfo: {
      number: Number.isFinite(page) ? page : 0,
      size: Number.isFinite(size) && size > 0 ? size : DEFAULT_ANOMALY_PAGE_SIZE,
      totalElements: Number.isFinite(totalElements) ? totalElements : 0,
      totalPages: Number.isFinite(totalPages) ? totalPages : 0,
      first: Boolean(data?.first ?? page <= 0),
      last: Boolean(data?.last ?? (totalPages === 0 || page + 1 >= totalPages)),
    },
  }
}

export function normalizeAnomalyStatistics(response) {
  const data = unwrapAnomalyResponse(response) ?? {}
  return {
    totalAnomalies: Number(data.totalAnomalies ?? 0),
    criticalAnomalies: Number(data.criticalAnomalies ?? 0),
    totalIncidents: Number(data.totalIncidents ?? 0),
    affectedServices: Number(data.affectedServices ?? 0),
    averageRiskScore: Number(data.averageRiskScore ?? 0),
    byLevel: Array.isArray(data.byLevel) ? data.byLevel : [],
    byType: Array.isArray(data.byType) ? data.byType : [],
    byDecision: Array.isArray(data.byDecision) ? data.byDecision : [],
    timeline: Array.isArray(data.timeline) ? data.timeline : [],
    topServices: Array.isArray(data.topServices) ? data.topServices : [],
    topEndpoints: Array.isArray(data.topEndpoints) ? data.topEndpoints : [],
    topMatchedRules: Array.isArray(data.topMatchedRules) ? data.topMatchedRules : [],
  }
}

export function normalizeAnomalyDetail(response) {
  return unwrapAnomalyResponse(response) ?? null
}

export function toIsoInstantFromInput(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toISOString()
}

export function formatAnomalyValue(value) {
  if (value === undefined || value === null || value === '') return EMPTY_ANOMALY_VALUE
  return String(value)
}

export function formatAnomalyTimestamp(value) {
  if (!value) return EMPTY_ANOMALY_VALUE
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return date.toLocaleString('vi-VN')
}

export function formatAnomalyNumber(value) {
  const number = Number(value)
  if (!Number.isFinite(number)) return EMPTY_ANOMALY_VALUE
  return number.toLocaleString('vi-VN')
}

export function riskScoreClass(score) {
  const value = Number(score)
  if (value >= 90) return 'anomaly-risk anomaly-risk--critical'
  if (value >= 75) return 'anomaly-risk anomaly-risk--high'
  if (value >= 50) return 'anomaly-risk anomaly-risk--medium'
  return 'anomaly-risk anomaly-risk--low'
}

export function anomalyChipClass(value, type = 'level') {
  const normalized = String(value || 'unknown').toLowerCase()
  return `anomaly-chip anomaly-chip--${type}-${normalized}`
}

export function anomalyDisplayName(item, nameField, idField) {
  return item?.[nameField] || item?.[idField] || EMPTY_ANOMALY_VALUE
}
