package vdt.mini.management_service.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class OutboundSettingsPatchRequest {
    private Integer timeoutMs;
    private Integer retryCount;
    private Integer retryBackoffMs;
    private Integer responseTimeThresholdMs;
    private Integer logRetentionDays;
    private String rollbackStrategy;
    private String alertSeverity;
    private Integer alertThrottleMinutes;
    private List<String> alertChannels;
}
