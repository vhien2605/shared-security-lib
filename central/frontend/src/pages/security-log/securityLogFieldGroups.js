import { formatBytes, formatDuration, formatTimestamp } from './securityLogMappers'

export const COMMON_FIELDS = [
  ['timestamp', 'Thời gian', formatTimestamp],
  ['traceId', 'Trace ID'],
  ['correlationId', 'Correlation ID'],
  ['flowType', 'Flow type'],
  ['direction', 'Direction'],
  ['serviceId', 'Service ID'],
  ['serviceName', 'Service name'],
  ['endpointId', 'Endpoint ID'],
  ['endpointName', 'Endpoint name'],
  ['protocol', 'Protocol'],
  ['method', 'Method'],
  ['status', 'Status'],
  ['resultCode', 'Result code'],
  ['errorCode', 'Error code'],
  ['durationMs', 'Duration', formatDuration],
  ['thresholdMs', 'Threshold', formatDuration],
  ['timeoutMs', 'Timeout', formatDuration],
  ['retentionDays', 'Retention days'],
  ['retentionBucket', 'Retention bucket'],
]

export const FLOW_FIELD_GROUPS = {
  INBOUND_HTTP: {
    title: 'Inbound HTTP',
    fields: [
      ['path', 'Path'], ['requestSizeBytes', 'Request size', formatBytes], ['responseSizeBytes', 'Response size', formatBytes],
      ['sourceIp', 'Source IP'], ['clientId', 'Client ID'], ['clientKey', 'Client key'], ['authType', 'Auth type'], ['denyReason', 'Deny reason'], ['rateLimit', 'Rate limit'],
      ['rateLimitWindowSeconds', 'Rate window (s)'], ['remainingQuota', 'Remaining quota'],
    ],
  },
  INBOUND_MQ: {
    title: 'Inbound MQ',
    fields: [
      ['topic', 'Topic'], ['messageSizeBytes', 'Message size', formatBytes], ['clientId', 'Client ID'], ['clientKey', 'Client key'],
      ['authType', 'Auth type'], ['denyReason', 'Deny reason'], ['rateLimit', 'Rate limit'], ['rateLimitWindowSeconds', 'Rate window (s)'],
      ['remainingQuota', 'Remaining quota'], ['consumerGroup', 'Consumer group'],
    ],
  },
  INBOUND_MQ_LISTENER: {
    title: 'Inbound MQ Listener',
    fields: [
      ['topic', 'Topic'], ['consumerGroup', 'Consumer group'], ['clientId', 'Client ID'], ['clientKey', 'Client key'],
      ['authType', 'Auth type'], ['denyReason', 'Deny reason'], ['messageSizeBytes', 'Message size', formatBytes],
      ['rateLimit', 'Rate limit'], ['rateLimitWindowSeconds', 'Rate window (s)'], ['remainingQuota', 'Remaining quota'],
    ],
  },
  OUTBOUND_HTTP: {
    title: 'Outbound HTTP',
    fields: [
      ['targetUrl', 'Target URL'], ['requestSizeBytes', 'Request size', formatBytes],
      ['responseSizeBytes', 'Response size', formatBytes], ['retryCount', 'Retry count'],
      ['retryAttempt', 'Retry attempt'], ['retryBackoffMs', 'Retry backoff', formatDuration], ['rollbackStrategy', 'Rollback strategy'],
    ],
  },
  OUTBOUND_MQ: {
    title: 'Outbound MQ',
    fields: [
      ['topic', 'Topic'], ['messageSizeBytes', 'Message size', formatBytes], ['producerClientId', 'Producer client ID'],
      ['retryCount', 'Retry count'], ['retryAttempt', 'Retry attempt'], ['retryBackoffMs', 'Retry backoff', formatDuration], ['rollbackStrategy', 'Rollback strategy'],
    ],
  },
}
