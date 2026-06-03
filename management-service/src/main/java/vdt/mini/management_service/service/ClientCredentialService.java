package vdt.mini.management_service.service;

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
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AuthConfigRepository authConfigRepository;

    public ClientCredentialService(AuthConfigRepository authConfigRepository) {
        this.authConfigRepository = authConfigRepository;
    }

    public CredentialMaterial getOrCreateCredential(String clientId, AuthType type) {
        Optional<AuthConfig> existing = authConfigRepository
                .findFirstByClientIdAndTypeAndSecretRefIsNotNullOrderByCreatedAtAsc(clientId, type);
        if (existing.isPresent()) {
            AuthConfig authConfig = existing.get();
            return new CredentialMaterial(authConfig.getSecretRef(), authConfig.getCredentialHash(), null, false);
        }

        String plaintext = generateSecret(type);
        return new CredentialMaterial("client-security:" + clientId + ":" + type.name().toLowerCase() + ":" + UUID.randomUUID(),
                sha256(plaintext), plaintext, true);
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

    public record CredentialMaterial(String secretRef, String credentialHash, String plaintext, boolean newlyGenerated) {
    }
}
