package vdt.mini.management_service.dto.sync;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RuntimeTombstoneDTO {
    private String resourceType;
    private String serviceId;
    private String endpointId;
    private String clientId;
    private String authConfigId;
    private String permissionId;
    private String reason;
}
