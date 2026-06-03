package vdt.mini.management_service.dto.request;

import lombok.Getter;
import lombok.Setter;
import vdt.mini.management_service.util.enums.EndpointStatus;

@Getter
@Setter
public class EndpointStatusPatchRequest {
    private EndpointStatus status;
}
