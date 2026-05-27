package vdt.mini.shared_lib.document;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ServiceRegistrationEvent {
    private String serviceId;
    private String serviceName;
    private String baseUrl;
    private String description;
    private List<InboundEndpointDTO> inbounds;
    private List<OutboundEndpointDTO> outbounds;
}
