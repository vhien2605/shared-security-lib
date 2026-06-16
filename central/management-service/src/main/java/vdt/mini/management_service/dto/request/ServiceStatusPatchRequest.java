package vdt.mini.management_service.dto.request;

import lombok.Getter;
import lombok.Setter;
import vdt.mini.management_service.util.enums.ServiceStatus;

@Getter
@Setter
public class ServiceStatusPatchRequest {
    private ServiceStatus status;
}
