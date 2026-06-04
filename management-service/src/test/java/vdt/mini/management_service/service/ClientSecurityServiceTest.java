package vdt.mini.management_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vdt.mini.management_service.dto.request.ClientAuthConfigChangesRequest;
import vdt.mini.management_service.dto.request.ClientAuthConfigCreateRequest;
import vdt.mini.management_service.dto.request.ClientCreateRequest;
import vdt.mini.management_service.dto.request.ClientUpdateRequest;
import vdt.mini.management_service.dto.response.ClientCredentialResponse;
import vdt.mini.management_service.dto.response.ClientUpdateResponse;
import vdt.mini.management_service.entity.AuthConfig;
import vdt.mini.management_service.entity.Client;
import vdt.mini.management_service.entity.SecureService;
import vdt.mini.management_service.exception.AppException;
import vdt.mini.management_service.repository.AuditLogRepository;
import vdt.mini.management_service.repository.AuthConfigRepository;
import vdt.mini.management_service.repository.ClientRepository;
import vdt.mini.management_service.repository.ServiceRepository;
import vdt.mini.management_service.util.enums.AuthType;
import vdt.mini.management_service.util.enums.ClientStatus;
import vdt.mini.management_service.util.enums.ErrorCode;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientSecurityServiceTest {
    @Mock
    private ClientRepository clientRepository;
    @Mock
    private AuthConfigRepository authConfigRepository;
    @Mock
    private ServiceRepository serviceRepository;
    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private ClientCredentialService credentialService;
    @Mock
    private ClientSecurityEventPublisher eventPublisher;

    private ClientSecurityService clientSecurityService;

    @BeforeEach
    void setUp() {
        clientSecurityService = new ClientSecurityService(clientRepository, authConfigRepository,
                serviceRepository, auditLogRepository, credentialService, eventPublisher, new ObjectMapper());
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void createClient_shouldPersistServiceScopedAuthConfig_whenServiceIdProvided() {
        SecureService service = secureService("service-1", "User Service");
        ClientCreateRequest request = createRequest(authConfig("service-1", "HMAC_SIGNATURE", "HmacSHA256"));
        when(serviceRepository.findById("service-1")).thenReturn(Optional.of(service));
        when(clientRepository.existsByClientKey(any())).thenReturn(false);
        when(authConfigRepository.findEnabledServiceConflicts(any(), any(), any())).thenReturn(List.of());
        when(credentialService.getOrCreateCredential(any(), any()))
                .thenReturn(new ClientCredentialService.CredentialMaterial("secret-ref", "hash", "ciphertext", null, false));

        clientSecurityService.createClient(request, null);

        ArgumentCaptor<AuthConfig> authConfigCaptor = ArgumentCaptor.forClass(AuthConfig.class);
        verify(authConfigRepository).save(authConfigCaptor.capture());
        AuthConfig savedAuthConfig = authConfigCaptor.getValue();
        assertSame(service, savedAuthConfig.getService());
        assertEquals(AuthType.HMAC_SIGNATURE, savedAuthConfig.getType());
        assertEquals("HmacSHA256", savedAuthConfig.getAlgorithm());
        assertEquals("ciphertext", savedAuthConfig.getSecretCiphertext());
    }

    @Test
    void createClient_shouldRejectNonSha256HmacAlgorithm() {
        SecureService service = secureService("service-1", "User Service");
        ClientCreateRequest request = createRequest(authConfig("service-1", "HMAC_SIGNATURE", "HmacSHA512"));
        when(serviceRepository.findById("service-1")).thenReturn(Optional.of(service));
        when(clientRepository.existsByClientKey(any())).thenReturn(false);

        AppException exception = assertThrows(AppException.class, () -> clientSecurityService.createClient(request, null));

        assertEquals(ErrorCode.INVALID_INPUT, exception.getErrorCode());
    }

    @Test
    void createClient_shouldKeepApiKeyCiphertextNull_whenApiKeyCredentialCreated() {
        SecureService service = secureService("service-1", "User Service");
        ClientCreateRequest request = createRequest(authConfig("service-1", "API_KEY", null));
        when(serviceRepository.findById("service-1")).thenReturn(Optional.of(service));
        when(clientRepository.existsByClientKey(any())).thenReturn(false);
        when(authConfigRepository.findEnabledServiceConflicts(any(), any(), any())).thenReturn(List.of());
        when(credentialService.getOrCreateCredential(any(), any()))
                .thenReturn(new ClientCredentialService.CredentialMaterial("secret-ref", "hash", null, "api-key", true));
        when(credentialService.toOneTimeResponse(eq(AuthType.API_KEY), any()))
                .thenReturn(ClientCredentialResponse.builder()
                        .type(AuthType.API_KEY.name())
                        .apiKey("api-key")
                        .secretRef("secret-ref")
                        .build());

        var response = clientSecurityService.createClient(request, null);

        ArgumentCaptor<AuthConfig> authConfigCaptor = ArgumentCaptor.forClass(AuthConfig.class);
        verify(authConfigRepository).save(authConfigCaptor.capture());
        AuthConfig savedAuthConfig = authConfigCaptor.getValue();
        assertEquals(AuthType.API_KEY, savedAuthConfig.getType());
        assertNull(savedAuthConfig.getSecretCiphertext());
        assertNotNull(response.getCredentials());
        assertEquals("api-key", response.getCredentials().get(0).getApiKey());
    }

    @Test
    void createClient_shouldRejectAuthConfigWithoutServiceId() {
        ClientCreateRequest request = createRequest(authConfig(null, "API_KEY", null));

        AppException exception = assertThrows(AppException.class, () -> clientSecurityService.createClient(request, null));

        assertEquals(ErrorCode.INVALID_INPUT, exception.getErrorCode());
    }

    @Test
    void updateClient_shouldReturnNewHmacCredential_whenAuthConfigAdded() {
        Client client = client("client-1");
        SecureService service = secureService("service-1", "User Service");
        ClientUpdateRequest request = updateRequest(authConfig("service-1", "HMAC_SIGNATURE", "HmacSHA256"));
        ClientCredentialService.CredentialMaterial material =
                new ClientCredentialService.CredentialMaterial("secret-ref", "hash", "ciphertext", "hs-secret", true);
        when(clientRepository.findByIdWithAuthConfigs("client-1")).thenReturn(Optional.of(client));
        when(serviceRepository.findById("service-1")).thenReturn(Optional.of(service));
        when(authConfigRepository.findEnabledServiceConflicts(any(), any(), any())).thenReturn(List.of());
        when(credentialService.getOrCreateCredential("client-1", AuthType.HMAC_SIGNATURE)).thenReturn(material);
        when(credentialService.toOneTimeResponse(eq(AuthType.HMAC_SIGNATURE), any()))
                .thenReturn(ClientCredentialResponse.builder()
                        .type(AuthType.HMAC_SIGNATURE.name())
                        .secretKey("hs-secret")
                        .secretRef("secret-ref")
                        .build());

        ClientUpdateResponse response = clientSecurityService.updateClient("client-1", request, null);

        assertEquals(1, response.getAuthConfigChanges().getCreated().size());
        assertEquals(1, response.getCredentials().size());
        assertEquals("hs-secret", response.getCredentials().get(0).getSecretKey());
    }

    @Test
    void updateClient_shouldReturnNewApiKeyCredential_whenAuthConfigAdded() {
        Client client = client("client-1");
        SecureService service = secureService("service-1", "User Service");
        ClientUpdateRequest request = updateRequest(authConfig("service-1", "API_KEY", null));
        ClientCredentialService.CredentialMaterial material =
                new ClientCredentialService.CredentialMaterial("secret-ref", "hash", null, "api-key", true);
        when(clientRepository.findByIdWithAuthConfigs("client-1")).thenReturn(Optional.of(client));
        when(serviceRepository.findById("service-1")).thenReturn(Optional.of(service));
        when(authConfigRepository.findEnabledServiceConflicts(any(), any(), any())).thenReturn(List.of());
        when(credentialService.getOrCreateCredential("client-1", AuthType.API_KEY)).thenReturn(material);
        when(credentialService.toOneTimeResponse(eq(AuthType.API_KEY), any()))
                .thenReturn(ClientCredentialResponse.builder()
                        .type(AuthType.API_KEY.name())
                        .apiKey("api-key")
                        .secretRef("secret-ref")
                        .build());

        ClientUpdateResponse response = clientSecurityService.updateClient("client-1", request, null);

        assertEquals(1, response.getCredentials().size());
        assertEquals("api-key", response.getCredentials().get(0).getApiKey());
    }

    @Test
    void updateClient_shouldNotReturnCredential_whenExistingCredentialReused() {
        Client client = client("client-1");
        SecureService service = secureService("service-1", "User Service");
        ClientUpdateRequest request = updateRequest(authConfig("service-1", "HMAC_SIGNATURE", "HmacSHA256"));
        ClientCredentialService.CredentialMaterial material =
                new ClientCredentialService.CredentialMaterial("secret-ref", "hash", "ciphertext", null, false);
        when(clientRepository.findByIdWithAuthConfigs("client-1")).thenReturn(Optional.of(client));
        when(serviceRepository.findById("service-1")).thenReturn(Optional.of(service));
        when(authConfigRepository.findEnabledServiceConflicts(any(), any(), any())).thenReturn(List.of());
        when(credentialService.getOrCreateCredential("client-1", AuthType.HMAC_SIGNATURE)).thenReturn(material);
        when(credentialService.toOneTimeResponse(eq(AuthType.HMAC_SIGNATURE), any())).thenReturn(null);

        ClientUpdateResponse response = clientSecurityService.updateClient("client-1", request, null);

        assertEquals(1, response.getAuthConfigChanges().getCreated().size());
        assertEquals(0, response.getCredentials().size());
    }

    @Test
    void updateClient_shouldPersistStatusWithRepositoryUpdate() {
        Client client = client("client-1");
        ClientUpdateRequest request = new ClientUpdateRequest();
        request.setStatus(ClientStatus.INACTIVE.name());
        when(clientRepository.findByIdWithAuthConfigs("client-1")).thenReturn(Optional.of(client));
        when(clientRepository.updateStatus(eq("client-1"), eq(ClientStatus.INACTIVE), isNull(), isNull()))
                .thenReturn(1);

        ClientUpdateResponse response = clientSecurityService.updateClient("client-1", request, null);

        assertEquals(ClientStatus.INACTIVE, response.getStatus());
        verify(clientRepository).updateStatus("client-1", ClientStatus.INACTIVE, null, null);
    }

    @Test
    void updateClient_shouldPersistRevokedMetadataWithRepositoryUpdate() {
        Client client = client("client-1");
        ClientUpdateRequest request = new ClientUpdateRequest();
        request.setStatus(ClientStatus.REVOKED.name());
        when(clientRepository.findByIdWithAuthConfigs("client-1")).thenReturn(Optional.of(client));
        when(clientRepository.updateStatus(eq("client-1"), eq(ClientStatus.REVOKED), any(), eq("system")))
                .thenReturn(1);

        ClientUpdateResponse response = clientSecurityService.updateClient("client-1", request, null);

        assertEquals(ClientStatus.REVOKED, response.getStatus());
        assertNotNull(client.getRevokedAt());
        assertEquals("system", client.getRevokedBy());
        verify(clientRepository).updateStatus(eq("client-1"), eq(ClientStatus.REVOKED), any(), eq("system"));
    }

    @Test
    void updateClient_shouldRejectDuplicateServiceAuthConfig() {
        Client client = client("client-1");
        SecureService service = secureService("service-1", "User Service");
        ClientUpdateRequest request = updateRequest(
                authConfig("service-1", "API_KEY", null),
                authConfig("service-1", "HMAC_SIGNATURE", "HmacSHA256"));
        when(clientRepository.findByIdWithAuthConfigs("client-1")).thenReturn(Optional.of(client));
        when(serviceRepository.findById("service-1")).thenReturn(Optional.of(service));

        AppException exception = assertThrows(AppException.class,
                () -> clientSecurityService.updateClient("client-1", request, null));

        assertEquals(ErrorCode.AUTH_CONFIG_CONFLICT, exception.getErrorCode());
    }

    private ClientCreateRequest createRequest(ClientAuthConfigCreateRequest authConfig) {
        ClientCreateRequest request = new ClientCreateRequest();
        request.setName("School Management System");
        request.setContactEmail("admin@school.example");
        request.setStatus(ClientStatus.ACTIVE.name());
        request.setAuthConfigs(List.of(authConfig));
        return request;
    }

    private ClientUpdateRequest updateRequest(ClientAuthConfigCreateRequest... authConfigs) {
        ClientAuthConfigChangesRequest changesRequest = new ClientAuthConfigChangesRequest();
        changesRequest.setAdd(List.of(authConfigs));
        ClientUpdateRequest request = new ClientUpdateRequest();
        request.setAuthConfigs(changesRequest);
        return request;
    }

    private ClientAuthConfigCreateRequest authConfig(String serviceId, String type, String algorithm) {
        ClientAuthConfigCreateRequest request = new ClientAuthConfigCreateRequest();
        request.setServiceId(serviceId);
        request.setType(type);
        request.setAlgorithm(algorithm);
        return request;
    }

    private SecureService secureService(String id, String name) {
        SecureService service = new SecureService();
        service.setId(id);
        service.setName(name);
        return service;
    }

    private Client client(String id) {
        Client client = new Client();
        client.setId(id);
        client.setClientKey("CLIENT-TEST");
        client.setName("School Management System");
        client.setContactEmail("admin@school.example");
        client.setStatus(ClientStatus.ACTIVE);
        return client;
    }
}
