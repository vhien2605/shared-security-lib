package vdt.mini.management_service.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ApplyServiceTemplateToEndpointsRequest {
    private List<String> endpointTypes;
    private List<String> endpointIds;
    private Long expectedTemplateVersion;
}
