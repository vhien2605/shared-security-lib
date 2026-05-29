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
public class InboundSettingsDTO {
    private String endpointId;
    private String name;
    private String path;
    private String method;
    private String protocol;
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
