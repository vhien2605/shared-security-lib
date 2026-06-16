package vdt.mini.management_service.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AccessPermissionCreateRequest {
    private String clientId;
    private String inboundEndpointId;
    private Boolean enable;
}
