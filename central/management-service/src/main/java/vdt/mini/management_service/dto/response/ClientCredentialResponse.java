package vdt.mini.management_service.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ClientCredentialResponse {
    private String type;
    private String apiKey;
    private String secretKey;
    private String secretRef;
}
