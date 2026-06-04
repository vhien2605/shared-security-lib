package vdt.mini.management_service.dto.sync;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RuntimeManifestDTO {
    private String serviceId;
    private Long version;
    private String generatedAt;
    private Integer inboundCount;
    private Integer outboundCount;
    private Integer clientCount;
    private Integer authConfigCount;
    private Integer permissionCount;
    private Map<String, String> keys;
}
