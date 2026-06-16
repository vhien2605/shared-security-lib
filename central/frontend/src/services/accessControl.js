import { apiDelete, apiGet, apiPatch, apiPost } from './api'
import { searchInbounds } from './clients'
import { RULE_TYPES, RULE_VALUE_TYPES, ACCESS_CONTROL_DEFAULT_PAGE_SIZE } from '../types/accessControl'

const ADMIN_PATH = '/api/admin'
const PERMISSIONS_PATH = `${ADMIN_PATH}/access-permissions`

export function buildQuery(params = {}) {
  const query = new URLSearchParams()

  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      query.set(key, String(value))
    }
  })

  const queryString = query.toString()
  return queryString ? `?${queryString}` : ''
}

export function unwrapResponse(response) {
  if (response && typeof response === 'object' && response.status !== undefined && response.status !== 200) {
    throw new Error(response.message || 'API request failed')
  }

  return response?.data ?? response
}

export function normalizePagePayload(response) {
  const data = unwrapResponse(response)
  const pageData = data?.data ?? data
  const items = pageData?.content || pageData?.items || pageData?.data || (Array.isArray(pageData) ? pageData : [])
  const size = Number(pageData?.size ?? pageData?.pageSize ?? ACCESS_CONTROL_DEFAULT_PAGE_SIZE)
  const number = Number(pageData?.number ?? pageData?.page ?? 0)
  const totalElements = Number(pageData?.totalElements ?? pageData?.total ?? items.length)
  const totalPages = Number(pageData?.totalPages ?? (size > 0 ? Math.ceil(totalElements / size) : 1))

  return {
    items: Array.isArray(items) ? items : [],
    pageInfo: {
      number: Number.isFinite(number) ? number : 0,
      size: Number.isFinite(size) && size > 0 ? size : ACCESS_CONTROL_DEFAULT_PAGE_SIZE,
      totalElements: Number.isFinite(totalElements) ? totalElements : 0,
      totalPages: Number.isFinite(totalPages) && totalPages > 0 ? totalPages : 1,
      first: Boolean(pageData?.first ?? number <= 0),
      last: Boolean(pageData?.last ?? number + 1 >= totalPages),
    },
  }
}

function assertId(value, name) {
  if (!value) throw new Error(`${name} is required`)
}

function assertRuleType(type) {
  if (!Object.values(RULE_TYPES).includes(type)) throw new Error('rule type is invalid')
}

function assertRuleValueType(valueType) {
  if (!Object.values(RULE_VALUE_TYPES).includes(valueType)) throw new Error('rule valueType is invalid')
}

export function listAccessRules(inboundEndpointId, { type, valueType = '', enable = '', keyword = '', page = 0, size = ACCESS_CONTROL_DEFAULT_PAGE_SIZE, sort = 'createdAt,desc' } = {}) {
  assertId(inboundEndpointId, 'inboundEndpointId')
  if (type) assertRuleType(type)
  if (valueType) assertRuleValueType(valueType)
  const query = buildQuery({ type, valueType, enable, keyword, page, size, sort })
  return apiGet(`${ADMIN_PATH}/inbound-endpoints/${encodeURIComponent(inboundEndpointId)}/access-rules${query}`)
}

export function listAllAccessRules({ type, inboundEndpointId = '', endpointKeyword = '', valueType = '', enable = '', keyword = '', page = 0, size = ACCESS_CONTROL_DEFAULT_PAGE_SIZE, sort = 'createdAt,desc' } = {}) {
  if (type) assertRuleType(type)
  if (valueType) assertRuleValueType(valueType)
  return apiGet(`${ADMIN_PATH}/access-rules${buildQuery({ type, inboundEndpointId, endpointKeyword, valueType, enable, keyword, page, size, sort })}`)
}

export function createAccessRule(inboundEndpointId, body) {
  assertId(inboundEndpointId, 'inboundEndpointId')
  assertRuleType(body?.type)
  assertRuleValueType(body?.valueType)
  if (!body?.value) throw new Error('value is required')
  if (body.temporary && !body.expiresAt) throw new Error('expiresAt is required for temporary rule')
  return apiPost(`${ADMIN_PATH}/inbound-endpoints/${encodeURIComponent(inboundEndpointId)}/access-rules`, body)
}

export function updateAccessRule(ruleId, enable) {
  assertId(ruleId, 'ruleId')
  if (typeof enable !== 'boolean') throw new Error('enable must be boolean')
  return apiPatch(`${ADMIN_PATH}/access-rules/${encodeURIComponent(ruleId)}`, { enable })
}

export function deleteAccessRule(ruleId) {
  assertId(ruleId, 'ruleId')
  return apiDelete(`${ADMIN_PATH}/access-rules/${encodeURIComponent(ruleId)}`)
}

export function listAccessPermissions({ clientId = '', inboundEndpointId = '', enable = '', keyword = '', page = 0, size = ACCESS_CONTROL_DEFAULT_PAGE_SIZE, sort = 'createdAt,desc' } = {}) {
  return apiGet(`${PERMISSIONS_PATH}${buildQuery({ clientId, inboundEndpointId, enable, keyword, page, size, sort })}`)
}

export function getAccessPermission(permissionId) {
  assertId(permissionId, 'permissionId')
  return apiGet(`${PERMISSIONS_PATH}/${encodeURIComponent(permissionId)}`)
}

export function createAccessPermission(body) {
  if (!body?.clientId) throw new Error('clientId is required')
  if (!body?.inboundEndpointId) throw new Error('inboundEndpointId is required')
  if (typeof body.enable !== 'boolean') throw new Error('enable must be boolean')
  return apiPost(PERMISSIONS_PATH, body)
}

export function updateAccessPermission(permissionId, enable) {
  assertId(permissionId, 'permissionId')
  if (typeof enable !== 'boolean') throw new Error('enable must be boolean')
  return apiPatch(`${PERMISSIONS_PATH}/${encodeURIComponent(permissionId)}`, { enable })
}

export function deleteAccessPermission(permissionId) {
  assertId(permissionId, 'permissionId')
  return apiDelete(`${PERMISSIONS_PATH}/${encodeURIComponent(permissionId)}`)
}

export { searchInbounds }
