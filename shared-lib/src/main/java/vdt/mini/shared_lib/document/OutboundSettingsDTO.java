package vdt.mini.shared_lib.document;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class OutboundSettingsDTO {
    private String endpointId;
    private String name;
    private String targetUrl;
    private String method;
    private String protocol;
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
