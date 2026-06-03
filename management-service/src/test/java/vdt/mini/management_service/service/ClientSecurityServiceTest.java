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
import vdt.mini.management_service.dto.request.ClientAuthConfigCreateRequest;
import vdt.mini.management_service.dto.request.ClientCreateRequest;
import vdt.mini.management_service.entity.AuthConfig;
import vdt.mini.management_service.entity.InboundEndpoint;
import vdt.mini.management_service.entity.SecureService;
import vdt.mini.management_service.exception.AppException;
import vdt.mini.management_service.repository.AuditLogRepository;
import vdt.mini.management_service.repository.AuthConfigRepository;
import vdt.mini.management_service.repository.ClientRepository;
import vdt.mini.management_service.repository.InboundEndpointRepository;
import vdt.mini.management_service.repository.ServiceRepository;
import vdt.mini.management_service.util.enums.AuthType;
import vdt.mini.management_service.util.enums.ClientStatus;
import vdt.mini.management_service.util.enums.ErrorCode;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientSecurityServiceTest {
    @Mock
    private ClientRepository clientRepository;
    @Mock
    private AuthConfigRepository authConfigRepository;
    @Mock
    private InboundEndpointRepository inboundEndpointRepository;
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
        clientSecurityService = new ClientSecurityService(clientRepository, authConfigRepository, inboundEndpointRepository,
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
        ClientCreateRequest request = createRequest(authConfig("service-1", null, "HMAC_SIGNATURE", "HmacSHA512"));
        when(serviceRepository.findById("service-1")).thenReturn(Optional.of(service));
        when(clientRepository.existsByClientKey(any())).thenReturn(false);
        when(authConfigRepository.findEnabledServiceConflicts(any(), any(), any())).thenReturn(List.of());
        when(credentialService.getOrCreateCredential(any(), any()))
                .thenReturn(new ClientCredentialService.CredentialMaterial("secret-ref", "hash", null, false));

        clientSecurityService.createClient(request, null);

        ArgumentCaptor<AuthConfig> authConfigCaptor = ArgumentCaptor.forClass(AuthConfig.class);
        verify(authConfigRepository).save(authConfigCaptor.capture());
        AuthConfig savedAuthConfig = authConfigCaptor.getValue();
        assertSame(service, savedAuthConfig.getService());
        assertNull(savedAuthConfig.getInboundEndpoint());
        assertEquals(AuthType.HMAC_SIGNATURE, savedAuthConfig.getType());
        assertEquals("HmacSHA512", savedAuthConfig.getAlgorithm());
    }

    @Test
    void createClient_shouldRejectMismatchedServiceAndLegacyInboundEndpoint() {
        SecureService requestedService = secureService("service-1", "User Service");
        SecureService inboundService = secureService("service-2", "Order Service");
        InboundEndpoint inboundEndpoint = new InboundEndpoint();
        inboundEndpoint.setId("inbound-1");
        inboundEndpoint.setSecureService(inboundService);
        ClientCreateRequest request = createRequest(authConfig("service-1", "inbound-1", "API_KEY", null));
        when(serviceRepository.findById("service-1")).thenReturn(Optional.of(requestedService));
        when(inboundEndpointRepository.findById("inbound-1")).thenReturn(Optional.of(inboundEndpoint));

        AppException exception = assertThrows(AppException.class, () -> clientSecurityService.createClient(request, null));

        assertEquals(ErrorCode.INVALID_INPUT, exception.getErrorCode());
    }

    private ClientCreateRequest createRequest(ClientAuthConfigCreateRequest authConfig) {
        ClientCreateRequest request = new ClientCreateRequest();
        request.setName("School Management System");
        request.setContactEmail("admin@school.example");
        request.setStatus(ClientStatus.ACTIVE.name());
        request.setAuthConfigs(List.of(authConfig));
        return request;
    }

    private ClientAuthConfigCreateRequest authConfig(String serviceId, String inboundEndpointId, String type, String algorithm) {
        ClientAuthConfigCreateRequest request = new ClientAuthConfigCreateRequest();
        request.setServiceId(serviceId);
        request.setInboundEndpointId(inboundEndpointId);
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
}
