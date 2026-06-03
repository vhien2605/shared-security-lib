package vdt.mini.management_service.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import vdt.mini.management_service.util.enums.AuthType;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class ClientAuthConfigChangeItemResponse {
    private String authConfigId;
    private String inboundEndpointId;
    private AuthType type;
    private Boolean enabled;
    private String secretRef;
    private LocalDateTime expiresAt;
    private LocalDateTime disabledAt;
}
