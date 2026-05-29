package vdt.mini.management_service.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class InboundSettingsPatchRequest {
    private Integer rateLimit;
    private Integer rateLimitWindowSeconds;
    private Integer timeoutMs;
    private Integer requestSizeLimitKb;
    private Integer responseSizeLimitKb;
    private Integer responseTimeThresholdMs;
    private Integer logRetentionDays;
    private String alertSeverity;
    private Integer alertThrottleMinutes;
    private List<String> alertChannels;
}
