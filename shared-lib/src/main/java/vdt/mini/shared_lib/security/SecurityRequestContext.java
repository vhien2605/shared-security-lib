package vdt.mini.shared_lib.security;

import vdt.mini.shared_lib.document.InboundSettingsDTO;

public class SecurityRequestContext {
    private String traceId;
    private String correlationId;
    private String serviceId;
    private String serviceName;
    private String endpointId;
    private String endpointName;
    private String protocol;
    private String method;
    private String path;
    private String topic;
    private String consumerGroup;
    private String clientId;
    private String clientKey;
    private String sourceIp;
    private String authType;
    private String denyReason;
    private long requestSizeBytes;
    private long responseSizeBytes;
    private long startedAtNanos;
    private long durationMs;
    private Integer thresholdMs;
    private Integer timeoutMs;
    private Integer rateLimit;
    private Integer rateLimitWindowSeconds;
    private Long remainingQuota;
    private Integer retentionDays;
    private String alertSeverity;
    private InboundSettingsDTO inboundSettings;

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public String getServiceId() { return serviceId; }
    public void setServiceId(String serviceId) { this.serviceId = serviceId; }
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public String getEndpointId() { return endpointId; }
    public void setEndpointId(String endpointId) { this.endpointId = endpointId; }
    public String getEndpointName() { return endpointName; }
    public void setEndpointName(String endpointName) { this.endpointName = endpointName; }
    public String getProtocol() { return protocol; }
    public void setProtocol(String protocol) { this.protocol = protocol; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public String getConsumerGroup() { return consumerGroup; }
    public void setConsumerGroup(String consumerGroup) { this.consumerGroup = consumerGroup; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getClientKey() { return clientKey; }
    public void setClientKey(String clientKey) { this.clientKey = clientKey; }
    public String getSourceIp() { return sourceIp; }
    public void setSourceIp(String sourceIp) { this.sourceIp = sourceIp; }
    public String getAuthType() { return authType; }
    public void setAuthType(String authType) { this.authType = authType; }
    public String getDenyReason() { return denyReason; }
    public void setDenyReason(String denyReason) { this.denyReason = denyReason; }
    public long getRequestSizeBytes() { return requestSizeBytes; }
    public void setRequestSizeBytes(long requestSizeBytes) { this.requestSizeBytes = requestSizeBytes; }
    public long getResponseSizeBytes() { return responseSizeBytes; }
    public void setResponseSizeBytes(long responseSizeBytes) { this.responseSizeBytes = responseSizeBytes; }
    public long getStartedAtNanos() { return startedAtNanos; }
    public void setStartedAtNanos(long startedAtNanos) { this.startedAtNanos = startedAtNanos; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    public Integer getThresholdMs() { return thresholdMs; }
    public void setThresholdMs(Integer thresholdMs) { this.thresholdMs = thresholdMs; }
    public Integer getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(Integer timeoutMs) { this.timeoutMs = timeoutMs; }
    public Integer getRateLimit() { return rateLimit; }
    public void setRateLimit(Integer rateLimit) { this.rateLimit = rateLimit; }
    public Integer getRateLimitWindowSeconds() { return rateLimitWindowSeconds; }
    public void setRateLimitWindowSeconds(Integer rateLimitWindowSeconds) { this.rateLimitWindowSeconds = rateLimitWindowSeconds; }
    public Long getRemainingQuota() { return remainingQuota; }
    public void setRemainingQuota(Long remainingQuota) { this.remainingQuota = remainingQuota; }
    public Integer getRetentionDays() { return retentionDays; }
    public void setRetentionDays(Integer retentionDays) { this.retentionDays = retentionDays; }
    public String getAlertSeverity() { return alertSeverity; }
    public void setAlertSeverity(String alertSeverity) { this.alertSeverity = alertSeverity; }
    public InboundSettingsDTO getInboundSettings() { return inboundSettings; }
    public void setInboundSettings(InboundSettingsDTO inboundSettings) { this.inboundSettings = inboundSettings; }
}
