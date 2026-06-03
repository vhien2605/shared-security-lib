package vdt.mini.management_service.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import vdt.mini.management_service.util.enums.ClientStatus;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class ClientUpdateResponse {
    private String id;
    private String clientCode;
    private String name;
    private String description;
    private String contactEmail;
    private ClientStatus status;
    private ClientAuthConfigChangesResponse authConfigChanges;
    private LocalDateTime updatedAt;
}
