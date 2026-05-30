package vdt.mini.shared_lib.document;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
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
