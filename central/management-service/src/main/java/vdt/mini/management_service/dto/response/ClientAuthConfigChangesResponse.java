package vdt.mini.management_service.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class ClientAuthConfigChangesResponse {
    private List<ClientAuthConfigChangeItemResponse> created;
    private List<ClientAuthConfigChangeItemResponse> updated;
    private List<ClientAuthConfigChangeItemResponse> removed;
}
