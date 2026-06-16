import { apiGet, apiPatch, apiPost, apiPut } from './api'

export const DEFAULT_GLOBAL_TEMPLATE = {
  version: 0,
  inboundRateLimit: 2000,
  inboundRateLimitWindowSeconds: 60,
  inboundTimeoutMs: 5000,
  inboundRequestSizeLimitKb: 1024,
  inboundResponseSizeLimitKb: 2048,
  inboundResponseTimeThresholdMs: 200,
  inboundLogRetentionDays: 15,
  outboundTimeoutMs: 10000,
  outboundRetryCount: 3,
  outboundRetryBackoffMs: 500,
  outboundResponseTimeThresholdMs: 500,
  outboundLogRetentionDays: 15,
  outboundRollbackStrategy: 'IGNORE',
  alertSeverity: 'CRITICAL',
  alertThrottleMinutes: 15,
  alertChannels: ['SLACK'],
}

export const ALERT_SEVERITIES = ['INFO', 'WARNING', 'CRITICAL']
export const ALERT_CHANNELS = ['SLACK', 'EMAIL', 'WEBHOOK']
export const ROLLBACK_STRATEGIES = ['IGNORE', 'COMPENSATE']
export const ENDPOINT_TYPES = ['INBOUND', 'OUTBOUND']

export const INBOUND_FIELDS = [
  'rateLimit',
  'rateLimitWindowSeconds',
  'timeoutMs',
  'requestSizeLimitKb',
  'responseSizeLimitKb',
  'responseTimeThresholdMs',
  'logRetentionDays',
]

export const OUTBOUND_FIELDS = [
  'timeoutMs',
  'retryCount',
  'retryBackoffMs',
  'responseTimeThresholdMs',
  'logRetentionDays',
]

export const TEMPLATE_NUMERIC_FIELDS = [
  'inboundRateLimit',
  'inboundRateLimitWindowSeconds',
  'inboundTimeoutMs',
  'inboundRequestSizeLimitKb',
  'inboundResponseSizeLimitKb',
  'inboundResponseTimeThresholdMs',
  'inboundLogRetentionDays',
  'outboundTimeoutMs',
  'outboundRetryCount',
  'outboundRetryBackoffMs',
  'outboundResponseTimeThresholdMs',
  'outboundLogRetentionDays',
  'alertThrottleMinutes',
]

export function unwrapResponse(response) {
  if (response && typeof response === 'object' && response.status !== undefined && response.status !== 200) {
    throw new Error(response.message || 'API request failed')
  }
  return response?.data ?? response
}

export function extractList(response) {
  const data = unwrapResponse(response)
  if (Array.isArray(data)) return data
  if (Array.isArray(data?.content)) return data.content
  return []
}

export function normalizeService(service) {
  if (!service) return service
  return { ...service, status: service.status || 'ACTIVE' }
}

export function normalizeEndpoint(endpoint) {
  if (!endpoint) return endpoint
  return { ...endpoint, status: endpoint.status || 'ACTIVE' }
}

export function endpointId(endpoint) {
  return endpoint?.id ?? endpoint?.endpointId
}

export function numberFromValue(value) {
  const parsed = Number(String(value).replace(/[^0-9-]/g, ''))
  return Number.isFinite(parsed) ? parsed : 0
}

export function textValue(value) {
  if (value === undefined || value === null || value === '') return ''
  return String(value).replace(/[^0-9-]/g, '')
}

export function toInputValue(value) {
  if (value === undefined || value === null) return ''
  return String(value)
}

export function normalizeChannels(channels) {
  if (Array.isArray(channels)) {
    return channels.map((channel) => String(channel).trim().toUpperCase()).filter(Boolean)
  }

  if (typeof channels === 'string') {
    const normalized = channels.trim()
    if (!normalized) return []
    try {
      const parsed = JSON.parse(normalized)
      if (Array.isArray(parsed)) return normalizeChannels(parsed)
    } catch {
      // Support comma/space separated values from older API shapes.
    }
    return normalized.split(/[\s,;|]+/).map((channel) => channel.trim().toUpperCase()).filter(Boolean)
  }

  return []
}

export function buildEndpointDraft(endpoint, numericFields) {
  const draft = numericFields.reduce((current, field) => ({ ...current, [field]: toInputValue(endpoint?.[field]) }), {})
  return {
    ...draft,
    rollbackStrategy: endpoint?.rollbackStrategy || 'IGNORE',
    alertSeverity: endpoint?.alertSeverity || 'CRITICAL',
    alertThrottleMinutes: toInputValue(endpoint?.alertThrottleMinutes),
    alertChannels: normalizeChannels(endpoint?.alertChannels),
  }
}

export function buildTemplateDraft(template) {
  if (!template) return null
  return {
    inboundRateLimit: toInputValue(template.inboundRateLimit),
    inboundRateLimitWindowSeconds: toInputValue(template.inboundRateLimitWindowSeconds),
    inboundTimeoutMs: toInputValue(template.inboundTimeoutMs),
    inboundRequestSizeLimitKb: toInputValue(template.inboundRequestSizeLimitKb),
    inboundResponseSizeLimitKb: toInputValue(template.inboundResponseSizeLimitKb),
    inboundResponseTimeThresholdMs: toInputValue(template.inboundResponseTimeThresholdMs),
    inboundLogRetentionDays: toInputValue(template.inboundLogRetentionDays),
    outboundTimeoutMs: toInputValue(template.outboundTimeoutMs),
    outboundRetryCount: toInputValue(template.outboundRetryCount),
    outboundRetryBackoffMs: toInputValue(template.outboundRetryBackoffMs),
    outboundResponseTimeThresholdMs: toInputValue(template.outboundResponseTimeThresholdMs),
    outboundLogRetentionDays: toInputValue(template.outboundLogRetentionDays),
    outboundRollbackStrategy: template.outboundRollbackStrategy || 'IGNORE',
    alertSeverity: template.alertSeverity || 'CRITICAL',
    alertThrottleMinutes: toInputValue(template.alertThrottleMinutes),
    alertChannels: normalizeChannels(template.alertChannels),
  }
}

export function parseNonNegativeInteger(value, label) {
  if (value === '' || value === undefined || value === null) throw new Error(`${label} là bắt buộc.`)
  if (!/^\d+$/.test(String(value))) throw new Error(`${label} phải là số nguyên không âm.`)
  return Number(value)
}

export function ensureOption(value, options, label) {
  if (!options.includes(value)) throw new Error(`${label} không hợp lệ.`)
  return value
}

export function validateChannels(channels) {
  if (!Array.isArray(channels)) return []
  const normalized = normalizeChannels(channels)
  const unsupported = normalized.find((channel) => !ALERT_CHANNELS.includes(channel))
  if (unsupported) throw new Error(`Kênh cảnh báo ${unsupported} không hợp lệ.`)
  return normalized
}

export function getGlobalSettings() {
  return Promise.all([
    apiGet('/central/api/configs/services?page=0&size=20'),
    apiGet('/central/api/configs/setting-templates/global'),
  ])
}

export function saveGlobalTemplate(body) {
  return apiPut('/central/api/configs/setting-templates/global', body)
}

export function applyGlobalTemplateToServices(body) {
  return apiPost('/central/api/configs/setting-templates/global/apply-to-services', body)
}

export function applyGlobalTemplateToEndpoints(body) {
  return apiPost('/central/api/configs/setting-templates/global/apply-to-endpoints', body)
}

export function updateServiceStatus(serviceId, status) {
  if (!serviceId) throw new Error('serviceId is required')
  return apiPatch(`/central/api/configs/services/${encodeURIComponent(serviceId)}/status`, { status })
}

export function getService(serviceId) {
  if (!serviceId) throw new Error('serviceId is required')
  return apiGet(`/central/api/configs/services/${encodeURIComponent(serviceId)}`)
}

export function getServiceInbounds(serviceId) {
  if (!serviceId) throw new Error('serviceId is required')
  return apiGet(`/central/api/configs/services/${encodeURIComponent(serviceId)}/inbounds`)
}

export function getServiceOutbounds(serviceId) {
  if (!serviceId) throw new Error('serviceId is required')
  return apiGet(`/central/api/configs/services/${encodeURIComponent(serviceId)}/outbounds`)
}

export function getServiceTemplate(serviceId) {
  if (!serviceId) throw new Error('serviceId is required')
  return apiGet(`/central/api/configs/services/${encodeURIComponent(serviceId)}/setting-template`)
}

export function updateInboundSettings(endpointIdValue, body) {
  if (!endpointIdValue) throw new Error('endpointId is required')
  return apiPatch(`/central/api/configs/inbounds/${encodeURIComponent(endpointIdValue)}/settings`, body)
}

export function updateOutboundSettings(endpointIdValue, body) {
  if (!endpointIdValue) throw new Error('endpointId is required')
  return apiPatch(`/central/api/configs/outbounds/${encodeURIComponent(endpointIdValue)}/settings`, body)
}

export function saveServiceTemplate(serviceId, body) {
  if (!serviceId) throw new Error('serviceId is required')
  return apiPut(`/central/api/configs/services/${encodeURIComponent(serviceId)}/setting-template`, body)
}

export function applyServiceTemplateToEndpoints(serviceId, body) {
  if (!serviceId) throw new Error('serviceId is required')
  return apiPost(`/central/api/configs/services/${encodeURIComponent(serviceId)}/setting-template/apply-to-endpoints`, body)
}

export function updateEndpointStatus(type, endpointIdValue, status) {
  if (!['inbound', 'outbound'].includes(type)) throw new Error('endpoint type is invalid')
  if (!endpointIdValue) throw new Error('endpointId is required')
  return apiPatch(`/central/api/configs/${type}s/${encodeURIComponent(endpointIdValue)}/status`, { status })
}
