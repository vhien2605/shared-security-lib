package vdt.mini.management_service.dto.sync;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AccessPermissionDTO {
    private String permissionId;
    private String clientId;
    private String clientKey;
    private String inboundEndpointId;
}
