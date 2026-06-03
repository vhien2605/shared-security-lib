package vdt.mini.management_service.dto.event;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
public class ClientSecurityConfigEvent {
    private String eventId;
    private String eventType;
    private LocalDateTime occurredAt;
    private String clientId;
    private String inboundEndpointId;
    private String accessRuleId;
    private List<String> authConfigIds;
    private List<String> changedFields;
    private long version;
}
