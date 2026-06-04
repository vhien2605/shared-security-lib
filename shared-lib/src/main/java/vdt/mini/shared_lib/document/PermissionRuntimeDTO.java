package vdt.mini.shared_lib.document;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class PermissionRuntimeDTO {
    private String permissionId;
    private String serviceId;
    private String inboundEndpointId;
    private String clientId;
    private String clientKey;
    private Boolean enabled;
    private String clientStatus;
    private Long version;
}
