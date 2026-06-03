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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientCredentialServiceTest {
    @Mock
    private AuthConfigRepository authConfigRepository;

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
        assertNotNull(response.getApiKey());
        assertNull(response.getSecretKey());
    }

    @Test
    void getOrCreateCredential_shouldReuseSecretRef_withoutPlaintext_whenCredentialExists() {
        AuthConfig existing = new AuthConfig();
        existing.setSecretRef("secret-ref");
        existing.setCredentialHash("hash");
        when(authConfigRepository.findFirstByClientIdAndTypeAndSecretRefIsNotNullOrderByCreatedAtAsc("client-1", AuthType.HMAC_SIGNATURE))
                .thenReturn(Optional.of(existing));

        ClientCredentialService.CredentialMaterial material = clientCredentialService.getOrCreateCredential("client-1", AuthType.HMAC_SIGNATURE);

        assertFalse(material.newlyGenerated());
        assertEquals("secret-ref", material.secretRef());
        assertEquals("hash", material.credentialHash());
        assertNull(material.plaintext());
        assertNull(clientCredentialService.toOneTimeResponse(AuthType.HMAC_SIGNATURE, material));
    }
}
