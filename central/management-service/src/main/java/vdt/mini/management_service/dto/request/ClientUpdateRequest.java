package vdt.mini.management_service.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClientUpdateRequest {
    private String name;
    private String description;
    private String contactEmail;
    private String status;
    private ClientAuthConfigChangesRequest authConfigs;
}
