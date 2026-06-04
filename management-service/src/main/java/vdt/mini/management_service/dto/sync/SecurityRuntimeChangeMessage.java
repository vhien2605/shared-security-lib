package vdt.mini.management_service.dto.sync;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SecurityRuntimeChangeMessage {
    private String eventId;
    private String eventType;
    private String serviceId;
    private String endpointId;
    private String clientId;
    private String authConfigId;
    private String permissionId;
    private List<String> changedFields;
    private Long version;
    private String occurredAt;
    private Object payload;
}
