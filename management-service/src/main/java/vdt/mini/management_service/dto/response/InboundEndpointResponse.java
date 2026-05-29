package vdt.mini.management_service.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import vdt.mini.management_service.util.enums.EndpointMethod;
import vdt.mini.management_service.util.enums.EndpointProtocol;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
public class InboundEndpointResponse {
    private String id;
    private String serviceId;
    private String name;
    private String path;
    private EndpointMethod method;
    private EndpointProtocol protocol;
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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
