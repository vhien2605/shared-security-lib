package vdt.mini.management_service.dto.sync;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuthConfigDTO {
    private String type;
    private String secretRef;
    private String credentialHash;
    private String publicKey;
    private String algorithm;
    private String expiresAt;
    private String clientKey;
    private Boolean enabled;
}
