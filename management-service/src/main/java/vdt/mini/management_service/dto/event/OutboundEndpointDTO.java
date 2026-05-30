package vdt.mini.management_service.dto.event;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OutboundEndpointDTO {
    private String endpointId;
    private String name;
    private String targetUrl;
    private String topic;
    private String method;
    private String protocol;
    private String description;
    private Boolean enabled;
}
