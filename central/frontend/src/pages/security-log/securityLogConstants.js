export const DEFAULT_PAGE_SIZE = 20
export const PAGE_SIZE_OPTIONS = [20, 50, 100]

export const DEFAULT_FILTERS = {
  from: '',
  to: '',
  serviceId: '',
  serviceName: '',
  endpointId: '',
  endpointName: '',
  flowType: '',
  direction: '',
  protocol: '',
  method: '',
  status: '',
  resultCode: '',
  errorCode: '',
  clientId: '',
  clientKey: '',
  traceId: '',
  correlationId: '',
  alertSeverity: '',
  target: '',
}

export const AUTO_REFRESH_OPTIONS = [
  { label: 'Off', value: 0 },
  { label: '5 giây', value: 5000 },
  { label: '10 giây', value: 10000 },
  { label: '30 giây', value: 30000 },
  { label: '60 giây', value: 60000 },
]

export const FLOW_TYPE_OPTIONS = [
  'INBOUND_HTTP',
  'INBOUND_MQ_LISTENER',
  'OUTBOUND_HTTP',
  'OUTBOUND_MQ_PUBLISHER',
]

export const DIRECTION_OPTIONS = ['INBOUND', 'OUTBOUND']
export const PROTOCOL_OPTIONS = ['HTTP', 'HTTPS', 'MQ', 'KAFKA', 'RABBITMQ', 'GRPC']
export const METHOD_OPTIONS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'PUBLISH', 'CONSUME']
export const STATUS_OPTIONS = ['SUCCESS', 'FAILED', 'DENIED', 'ERROR', 'TIMEOUT', 'RETRY']
export const SEVERITY_OPTIONS = ['INFO', 'LOW', 'MEDIUM', 'WARNING', 'HIGH', 'CRITICAL']
