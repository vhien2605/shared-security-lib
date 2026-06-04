package vdt.mini.shared_lib.document;

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
