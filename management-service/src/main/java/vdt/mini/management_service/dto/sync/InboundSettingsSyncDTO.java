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
public class InboundSettingsSyncDTO {
    private String endpointId;
    private String name;
    private String path;
    private String topic;
    private String method;
    private String protocol;
    private Boolean enabled;
    private String endpointStatus;
    private String serviceStatus;
    private Boolean available;
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
    private List<AuthConfigDTO> authConfigs;
    private List<AccessRuleDTO> accessRules;
}
