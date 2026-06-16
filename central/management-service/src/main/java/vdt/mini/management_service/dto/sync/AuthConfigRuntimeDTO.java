package vdt.mini.management_service.dto.sync;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuthConfigRuntimeDTO {
    private String authConfigId;
    private String serviceId;
    private String clientId;
    private String clientKey;
    private String type;
    private String secretRef;
    private String credentialHash;
    private String algorithm;
    private String publicKey;
    private String secretKey;
    private String expiresAt;
    private Boolean enabled;
    private String clientStatus;
    private Long version;
}
