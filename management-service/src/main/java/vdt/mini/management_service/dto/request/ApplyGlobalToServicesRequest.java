package vdt.mini.management_service.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ApplyGlobalToServicesRequest {
    private List<String> serviceIds;
    private Long expectedTemplateVersion;
}
