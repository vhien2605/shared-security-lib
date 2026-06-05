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
public class SettingsChangeMessage {
    private String type;
    private String endpointId;
    private String serviceId;
    private Object config;
    private String operation;
    private Long version;
    private String occurredAt;
    private List<String> changedFields;

    public SettingsChangeMessage(String type, String endpointId, String serviceId, Object config) {
        this.type = type;
        this.endpointId = endpointId;
        this.serviceId = serviceId;
        this.config = config;
    }
}
