import { apiGet } from './api'

const ANOMALIES_PATH = '/central/api/admin/anomalies'

export function buildAnomalyQuery(params = {}) {
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

export function getAnomalies(params = {}, options = {}) {
  return apiGet(`${ANOMALIES_PATH}${buildAnomalyQuery(params)}`, options)
}

export function getAnomalyDetail(anomalyId, options = {}) {
  return apiGet(`${ANOMALIES_PATH}/${encodeURIComponent(anomalyId)}`, options)
}

export function getAnomalyStatistics(params = {}, options = {}) {
  return apiGet(`${ANOMALIES_PATH}/statistics${buildAnomalyQuery(params)}`, options)
}
