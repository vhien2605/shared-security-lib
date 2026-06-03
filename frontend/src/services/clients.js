import { apiGet, apiPost, apiPut } from './api'

const CLIENTS_PATH = '/api/admin/clients'
const INBOUNDS_SEARCH_PATH = '/central/api/configs/inbound-endpoints/search'
const SERVICES_SEARCH_PATH = '/central/api/configs/services/search'

function buildQuery(params) {
  const query = new URLSearchParams()

  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      query.set(key, String(value))
    }
  })

  const queryString = query.toString()
  return queryString ? `?${queryString}` : ''
}

export function getClients({ keyword = '', status = '', page = 0, size = 20, sort = 'createdAt,desc' } = {}) {
  return apiGet(`${CLIENTS_PATH}${buildQuery({ keyword, status, page, size, sort })}`)
}

export function getClient(clientId) {
  if (!clientId) throw new Error('clientId is required')
  return apiGet(`${CLIENTS_PATH}/${encodeURIComponent(clientId)}`)
}

export function createClient(body) {
  return apiPost(CLIENTS_PATH, body)
}

export function updateClient(clientId, body) {
  if (!clientId) throw new Error('clientId is required')
  return apiPut(`${CLIENTS_PATH}/${encodeURIComponent(clientId)}`, body)
}

export function searchInbounds({ name = '', size = 10 } = {}) {
  return apiGet(`${INBOUNDS_SEARCH_PATH}${buildQuery({ name, size })}`)
}

export function searchServices({ name = '', size = 10 } = {}) {
  return apiGet(`${SERVICES_SEARCH_PATH}${buildQuery({ name, size })}`)
}
