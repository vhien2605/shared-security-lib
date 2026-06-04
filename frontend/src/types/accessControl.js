export const RULE_TYPES = Object.freeze({
  BLACKLIST: 'BLACKLIST',
  WHITELIST: 'WHITELIST',
})

export const RULE_VALUE_TYPES = Object.freeze({
  IP: 'IP',
  CIDR: 'CIDR',
  CLIENT_ID: 'CLIENT_ID',
  HEADER: 'HEADER',
})

export const ACCESS_CONTROL_DEFAULT_PAGE_SIZE = 10

/**
 * @typedef {Object} AccessRuleResponse
 * @property {string} id
 * @property {string} inboundEndpointId
 * @property {string} [inboundEndpointName]
 * @property {string} [inboundEndpointPath]
 * @property {string} [serviceId]
 * @property {string} [serviceName]
 * @property {'BLACKLIST'|'WHITELIST'} type
 * @property {'IP'|'CIDR'|'CLIENT_ID'|'HEADER'} valueType
 * @property {string} value
 * @property {boolean} temporary
 * @property {boolean} enable
 * @property {string} [expiresAt]
 * @property {string} [reason]
 * @property {string} [createdAt]
 */

/**
 * @typedef {Object} AccessRuleCreateRequest
 * @property {'BLACKLIST'|'WHITELIST'} type
 * @property {'IP'|'CIDR'|'CLIENT_ID'|'HEADER'} valueType
 * @property {string} value
 * @property {boolean} temporary
 * @property {boolean} enable
 * @property {string} [expiresAt]
 * @property {string} [reason]
 */

/**
 * @typedef {Object} AccessPermissionResponse
 * @property {string} id
 * @property {string} clientId
 * @property {string} [clientKey]
 * @property {string} [clientName]
 * @property {string} inboundEndpointId
 * @property {string} [inboundEndpointName]
 * @property {string} [inboundEndpointPath]
 * @property {string} [serviceId]
 * @property {string} [serviceName]
 * @property {boolean} enable
 * @property {string} [createdAt]
 * @property {string} [updatedAt]
 */
