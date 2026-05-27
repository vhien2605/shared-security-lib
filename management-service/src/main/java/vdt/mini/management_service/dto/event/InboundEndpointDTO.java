package vdt.mini.management_service.dto.event;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InboundEndpointDTO {
    private String endpointId;
    private String name;
    private String path;
    private String topic;
    private String method;
    private String protocol;
    private String description;
}
