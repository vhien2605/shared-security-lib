package vdt.mini.management_service.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class AccessPermissionResponse {
    private String id;
    private String clientId;
    private String clientKey;
    private String clientCode;
    private String clientName;
    private String inboundEndpointId;
    private String inboundEndpointName;
    private String endpointName;
    private String inboundEndpointPath;
    private String serviceId;
    private String serviceName;
    private Boolean enable;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
