package vdt.mini.management_service.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SettingTemplateUpdateRequest {
    private Long expectedVersion;
    private Integer inboundRateLimit;
    private Integer inboundRateLimitWindowSeconds;
    private Integer inboundTimeoutMs;
    private Integer inboundRequestSizeLimitKb;
    private Integer inboundResponseSizeLimitKb;
    private Integer inboundResponseTimeThresholdMs;
    private Integer inboundLogRetentionDays;
    private Integer outboundTimeoutMs;
    private Integer outboundRetryCount;
    private Integer outboundRetryBackoffMs;
    private Integer outboundResponseTimeThresholdMs;
    private Integer outboundLogRetentionDays;
    private String outboundRollbackStrategy;
    private String alertSeverity;
    private Integer alertThrottleMinutes;
    private List<String> alertChannels;
}
