import { apiGet } from './api'

const SECURITY_LOGS_PATH = '/central/api/admin/security-logs'

export function buildSecurityLogQuery(params = {}) {
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null) return
    const normalized = typeof value === 'string' ? value.trim() : value
    if (normalized === '') return
    query.set(key, String(normalized))
  })
  const queryString = query.toString()
  return queryString ? `?${queryString}` : ''
}

export function getSecurityLogs(params = {}, options = {}) {
  return apiGet(`${SECURITY_LOGS_PATH}${buildSecurityLogQuery(params)}`, options)
}
