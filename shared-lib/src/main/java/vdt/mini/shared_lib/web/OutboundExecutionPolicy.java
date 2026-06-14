package vdt.mini.shared_lib.web;

import vdt.mini.shared_lib.document.OutboundSettingsDTO;

public record OutboundExecutionPolicy(
        String endpointId,
        String endpointName,
        String serviceId,
        String serviceName,
        String targetUrl,
        String topic,
        String method,
        String protocol,
        int timeoutMs,
        int retryCount,
        int retryBackoffMs,
        Integer responseTimeThresholdMs,
        Integer logRetentionDays,
        String rollbackStrategy,
        String alertSeverity,
        Integer alertThrottleMinutes,
        java.util.List<String> alertChannels,
        OutboundSettingsDTO settings) {
}
