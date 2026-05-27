package vdt.mini.management_service.dto.event;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ServiceRegistrationEvent {
    private String serviceId;
    private String serviceName;
    private String baseUrl;
    private String description;
    private List<InboundEndpointDTO> inbounds;
    private List<OutboundEndpointDTO> outbounds;
}
