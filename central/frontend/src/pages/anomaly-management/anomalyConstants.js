export const DEFAULT_ANOMALY_PAGE_SIZE = 20
export const ANOMALY_PAGE_SIZE_OPTIONS = [20, 50, 100]

export const DEFAULT_ANOMALY_FILTERS = {
  from: '',
  to: '',
  serviceId: '',
  endpointId: '',
  anomalyType: '',
  anomalyLevel: '',
  decision: '',
  sourceType: '',
  flowType: '',
  eventDirection: '',
  incidentId: '',
  traceId: '',
  minRiskScore: '',
  maxRiskScore: '',
}

export const ANOMALY_AUTO_REFRESH_OPTIONS = [
  { label: 'Off', value: 0 },
  { label: '5 giây', value: 5000 },
  { label: '10 giây', value: 10000 },
  { label: '30 giây', value: 30000 },
  { label: '1 phút', value: 60000 },
  { label: '5 phút', value: 300000 },
]

export const ANOMALY_TYPE_OPTIONS = [
  'LATENCY_OUTLIER',
  'REQUEST_SIZE_OUTLIER',
  'RESPONSE_SIZE_OUTLIER',
  'MESSAGE_SIZE_OUTLIER',
  'RETRY_OUTLIER',
  'NEW_OR_RARE_CLIENT',
  'NEW_OR_RARE_SOURCE_IP',
  'RARE_ERROR_CODE',
  'FAILURE_SPIKE',
  'DENIED_SPIKE',
  'RETRY_SPIKE',
  'TRAFFIC_SPIKE',
  'TRAFFIC_DROP',
  'LATENCY_DRIFT',
  'REQUEST_SIZE_DRIFT',
  'RESPONSE_SIZE_DRIFT',
  'MESSAGE_SIZE_DRIFT',
  'SOURCE_IP_SPIKE',
  'CLIENT_DIVERSITY_SPIKE',
  'ERROR_DISTRIBUTION_DRIFT',
  'UPSTREAM_DEGRADATION',
  'DEPENDENCY_INSTABILITY',
  'AUTHENTICATION_ATTACK_PATTERN',
]

export const ANOMALY_LEVEL_OPTIONS = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']
export const ANOMALY_DECISION_OPTIONS = ['NORMAL', 'OBSERVE', 'SUSPICIOUS', 'ANOMALY']
export const ANOMALY_SOURCE_TYPE_OPTIONS = ['RUNTIME_SECURITY_LOG', 'LOG_BASELINE', 'BEHAVIOR_BASELINE', 'MANUAL']
export const ANOMALY_FLOW_TYPE_OPTIONS = ['INBOUND_HTTP', 'INBOUND_MQ_LISTENER', 'OUTBOUND_HTTP', 'OUTBOUND_MQ']
export const ANOMALY_DIRECTION_OPTIONS = ['INBOUND', 'OUTBOUND']
