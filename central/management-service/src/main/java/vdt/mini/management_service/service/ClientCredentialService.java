package vdt.mini.management_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vdt.mini.management_service.dto.response.ClientCredentialResponse;
import vdt.mini.management_service.entity.AuthConfig;
import vdt.mini.management_service.repository.AuthConfigRepository;
import vdt.mini.management_service.util.enums.AuthType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class ClientCredentialService {
    private static final Logger log = LoggerFactory.getLogger(ClientCredentialService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AuthConfigRepository authConfigRepository;
    private final SecretCipherService secretCipherService;

    public ClientCredentialService(AuthConfigRepository authConfigRepository, SecretCipherService secretCipherService) {
        this.authConfigRepository = authConfigRepository;
        this.secretCipherService = secretCipherService;
    }

    public CredentialMaterial getOrCreateCredential(String clientId, AuthType type) {
        Optional<AuthConfig> existing = authConfigRepository
                .findFirstByClientIdAndTypeAndSecretRefIsNotNullOrderByCreatedAtAsc(clientId, type);
        if (existing.isPresent()) {
            AuthConfig authConfig = existing.get();
            if (type == AuthType.API_KEY || hasText(authConfig.getSecretCiphertext())) {
                return new CredentialMaterial(authConfig.getSecretRef(), authConfig.getCredentialHash(),
                        authConfig.getSecretCiphertext(), null, false);
            }
            log.warn("Existing HMAC credential for clientId={} has no ciphertext; generating new HMAC credential", clientId);
        }

        String plaintext = generateSecret(type);
        String secretCiphertext = type == AuthType.HMAC_SIGNATURE ? secretCipherService.encrypt(plaintext) : null;
        return new CredentialMaterial("client-security:" + clientId + ":" + type.name().toLowerCase() + ":" + UUID.randomUUID(),
                sha256(plaintext), secretCiphertext, plaintext, true);
    }

    public ClientCredentialResponse toOneTimeResponse(AuthType type, CredentialMaterial material) {
        if (!material.newlyGenerated()) {
            return null;
        }
        return ClientCredentialResponse.builder()
                .type(type.name())
                .apiKey(type == AuthType.API_KEY ? material.plaintext() : null)
                .secretKey(type == AuthType.HMAC_SIGNATURE ? material.plaintext() : null)
                .secretRef(material.secretRef())
                .build();
    }

    private String generateSecret(AuthType type) {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        String prefix = type == AuthType.API_KEY ? "ak_" : "hs_";
        return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record CredentialMaterial(String secretRef, String credentialHash, String secretCiphertext, String plaintext,
                                     boolean newlyGenerated) {
    }
}
