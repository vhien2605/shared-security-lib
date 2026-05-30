package vdt.mini.shared_lib.document;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InboundEndpointDTO {
    private String endpointId;
    private String name;
    private String path;
    private String topic;
    private String method;
    private String protocol;
    private String description;
    private Boolean enabled;
}
