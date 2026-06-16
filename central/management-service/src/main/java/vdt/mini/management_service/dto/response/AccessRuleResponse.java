package vdt.mini.management_service.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import vdt.mini.management_service.util.enums.AccessRuleType;
import vdt.mini.management_service.util.enums.AccessRuleValueType;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class AccessRuleResponse {
    private String id;
    private String inboundEndpointId;
    private String inboundEndpointName;
    private String inboundEndpointPath;
    private String serviceId;
    private String serviceName;
    private AccessRuleType type;
    private AccessRuleValueType valueType;
    private String value;
    private Boolean temporary;
    private Boolean enable;
    private LocalDateTime expiresAt;
    private String reason;
    private LocalDateTime createdAt;
}
