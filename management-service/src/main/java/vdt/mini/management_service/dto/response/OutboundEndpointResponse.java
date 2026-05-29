package vdt.mini.management_service.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import vdt.mini.management_service.util.enums.EndpointMethod;
import vdt.mini.management_service.util.enums.EndpointProtocol;
import vdt.mini.management_service.util.enums.RollbackStrategy;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
public class OutboundEndpointResponse {
    private String id;
    private String serviceId;
    private String name;
    private String targetUrl;
    private EndpointMethod method;
    private EndpointProtocol protocol;
    private Integer timeoutMs;
    private Integer retryCount;
    private Integer retryBackoffMs;
    private Integer responseTimeThresholdMs;
    private Integer logRetentionDays;
    private RollbackStrategy rollbackStrategy;
    private String alertSeverity;
    private Integer alertThrottleMinutes;
    private List<String> alertChannels;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
