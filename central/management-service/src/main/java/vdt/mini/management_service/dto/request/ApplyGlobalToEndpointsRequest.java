package vdt.mini.management_service.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ApplyGlobalToEndpointsRequest {
    private List<String> serviceIds;
    private List<String> endpointTypes;
    private Long expectedTemplateVersion;
}
