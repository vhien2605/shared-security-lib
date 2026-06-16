package vdt.mini.management_service.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class SettingTemplateResponse {
    private String id;
    private String level;
    private String serviceId;
    private Long version;
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
