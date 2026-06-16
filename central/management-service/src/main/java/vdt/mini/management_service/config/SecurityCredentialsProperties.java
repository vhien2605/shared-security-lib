package vdt.mini.management_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Base64;

@Component
@ConfigurationProperties(prefix = "security.credentials")
public class SecurityCredentialsProperties {
    private static final int AES_256_KEY_BYTES = 32;

    private String masterSecret;

    public String getMasterSecret() {
        return masterSecret;
    }

    public void setMasterSecret(String masterSecret) {
        this.masterSecret = masterSecret;
    }

    public byte[] requireMasterSecretBytes() {
        if (masterSecret == null || masterSecret.isBlank()) {
            throw new IllegalStateException("security.credentials.master-secret must be configured");
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(masterSecret.trim());
            if (decoded.length != AES_256_KEY_BYTES) {
                throw new IllegalStateException("security.credentials.master-secret must decode to 32 bytes");
            }
            return decoded;
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("security.credentials.master-secret must be base64 encoded", ex);
        }
    }
}
