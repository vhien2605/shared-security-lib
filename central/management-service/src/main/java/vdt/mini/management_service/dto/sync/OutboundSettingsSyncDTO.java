package vdt.mini.management_service.dto.sync;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OutboundSettingsSyncDTO {
    private String endpointId;
    private String name;
    private String targetUrl;
    private String topic;
    private String method;
    private String protocol;
    private Boolean enabled;
    private String endpointStatus;
    private String serviceStatus;
    private Boolean available;
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
