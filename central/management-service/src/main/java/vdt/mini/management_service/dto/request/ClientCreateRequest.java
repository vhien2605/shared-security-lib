package vdt.mini.management_service.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ClientCreateRequest {
    private String clientCode;
    private String name;
    private String description;
    private String contactEmail;
    private String status;
    private List<ClientAuthConfigCreateRequest> authConfigs = new ArrayList<>();
}
