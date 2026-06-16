package vdt.mini.management_service.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vdt.mini.management_service.dto.response.ClientCredentialResponse;
import vdt.mini.management_service.entity.AuthConfig;
import vdt.mini.management_service.repository.AuthConfigRepository;
import vdt.mini.management_service.util.enums.AuthType;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientCredentialServiceTest {
    @Mock
    private AuthConfigRepository authConfigRepository;
    @Mock
    private SecretCipherService secretCipherService;

    @InjectMocks
    private ClientCredentialService clientCredentialService;

    @Test
    void getOrCreateCredential_shouldGenerateOneTimeApiKey_whenNoCredentialExists() {
        when(authConfigRepository.findFirstByClientIdAndTypeAndSecretRefIsNotNullOrderByCreatedAtAsc("client-1", AuthType.API_KEY))
                .thenReturn(Optional.empty());

        ClientCredentialService.CredentialMaterial material = clientCredentialService.getOrCreateCredential("client-1", AuthType.API_KEY);
        ClientCredentialResponse response = clientCredentialService.toOneTimeResponse(AuthType.API_KEY, material);

        assertTrue(material.newlyGenerated());
        assertNotNull(material.secretRef());
        assertNotNull(material.credentialHash());
        assertNull(material.secretCiphertext());
        assertNotNull(response.getApiKey());
        assertNull(response.getSecretKey());
        verifyNoInteractions(secretCipherService);
    }

    @Test
    void getOrCreateCredential_shouldGenerateEncryptedHmacSecret_whenNoCredentialExists() {
        when(authConfigRepository.findFirstByClientIdAndTypeAndSecretRefIsNotNullOrderByCreatedAtAsc("client-1", AuthType.HMAC_SIGNATURE))
                .thenReturn(Optional.empty());
        when(secretCipherService.encrypt(org.mockito.ArgumentMatchers.anyString())).thenReturn("ciphertext");

        ClientCredentialService.CredentialMaterial material = clientCredentialService.getOrCreateCredential("client-1", AuthType.HMAC_SIGNATURE);
        ClientCredentialResponse response = clientCredentialService.toOneTimeResponse(AuthType.HMAC_SIGNATURE, material);

        assertTrue(material.newlyGenerated());
        assertEquals("ciphertext", material.secretCiphertext());
        assertNotNull(material.plaintext());
        assertNull(response.getApiKey());
        assertEquals(material.plaintext(), response.getSecretKey());
        verify(secretCipherService).encrypt(material.plaintext());
    }

    @Test
    void getOrCreateCredential_shouldReuseHmacCiphertext_withoutPlaintext_whenCredentialExists() {
        AuthConfig existing = new AuthConfig();
        existing.setSecretRef("secret-ref");
        existing.setCredentialHash("hash");
        existing.setSecretCiphertext("ciphertext");
        when(authConfigRepository.findFirstByClientIdAndTypeAndSecretRefIsNotNullOrderByCreatedAtAsc("client-1", AuthType.HMAC_SIGNATURE))
                .thenReturn(Optional.of(existing));

        ClientCredentialService.CredentialMaterial material = clientCredentialService.getOrCreateCredential("client-1", AuthType.HMAC_SIGNATURE);

        assertFalse(material.newlyGenerated());
        assertEquals("secret-ref", material.secretRef());
        assertEquals("hash", material.credentialHash());
        assertEquals("ciphertext", material.secretCiphertext());
        assertNull(material.plaintext());
        assertNull(clientCredentialService.toOneTimeResponse(AuthType.HMAC_SIGNATURE, material));
        verifyNoInteractions(secretCipherService);
    }

    @Test
    void getOrCreateCredential_shouldGenerateNewHmacCredential_whenExistingHmacHasNoCiphertext() {
        AuthConfig existing = new AuthConfig();
        existing.setSecretRef("legacy-secret-ref");
        existing.setCredentialHash("legacy-hash");
        when(authConfigRepository.findFirstByClientIdAndTypeAndSecretRefIsNotNullOrderByCreatedAtAsc("client-1", AuthType.HMAC_SIGNATURE))
                .thenReturn(Optional.of(existing));
        when(secretCipherService.encrypt(org.mockito.ArgumentMatchers.anyString())).thenReturn("new-ciphertext");

        ClientCredentialService.CredentialMaterial material = clientCredentialService.getOrCreateCredential("client-1", AuthType.HMAC_SIGNATURE);

        assertTrue(material.newlyGenerated());
        assertEquals("new-ciphertext", material.secretCiphertext());
        assertNotNull(material.plaintext());
        assertFalse("legacy-secret-ref".equals(material.secretRef()));
    }
}
