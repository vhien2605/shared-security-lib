package vdt.mini.management_service.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ClientAuthConfigChangesRequest {
    private List<ClientAuthConfigCreateRequest> add = new ArrayList<>();
    private List<ClientAuthConfigUpdateRequest> update = new ArrayList<>();
    private List<String> removeAuthConfigIds = new ArrayList<>();
}
