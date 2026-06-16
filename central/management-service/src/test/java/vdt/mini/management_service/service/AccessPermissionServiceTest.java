package vdt.mini.management_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vdt.mini.management_service.dto.request.AccessPermissionCreateRequest;
import vdt.mini.management_service.dto.request.AccessPermissionUpdateRequest;
import vdt.mini.management_service.dto.response.AccessPermissionResponse;
import vdt.mini.management_service.entity.AccessPermission;
import vdt.mini.management_service.entity.Client;
import vdt.mini.management_service.entity.InboundEndpoint;
import vdt.mini.management_service.entity.SecureService;
import vdt.mini.management_service.exception.AppException;
import vdt.mini.management_service.repository.AccessPermissionRepository;
import vdt.mini.management_service.repository.AuditLogRepository;
import vdt.mini.management_service.repository.ClientRepository;
import vdt.mini.management_service.repository.InboundEndpointRepository;
import vdt.mini.management_service.util.enums.ClientStatus;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessPermissionServiceTest {
    @Mock
    private AccessPermissionRepository accessPermissionRepository;
    @Mock
    private ClientRepository clientRepository;
    @Mock
    private InboundEndpointRepository inboundEndpointRepository;
    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private RedisSettingsSyncService redisSettingsSyncService;
    @Mock
    private Authentication authentication;

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void createPermission_shouldDefaultEnableTrueAndTriggerRedisSyncAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        AccessPermissionService service = newService();
        Client client = client("client-1", ClientStatus.ACTIVE);
        InboundEndpoint endpoint = endpoint("endpoint-1", "service-1");
        when(clientRepository.findById("client-1")).thenReturn(Optional.of(client));
        when(inboundEndpointRepository.findById("endpoint-1")).thenReturn(Optional.of(endpoint));
        when(accessPermissionRepository.existsByClientIdAndInboundEndpointId("client-1", "endpoint-1")).thenReturn(false);
        when(accessPermissionRepository.save(any(AccessPermission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccessPermissionResponse response = service.createPermission(createRequest("client-1", "endpoint-1", null), authentication);
        TransactionSynchronizationManager.getSynchronizations().forEach(synchronization -> synchronization.afterCommit());

        assertNotNull(response.getId());
        assertEquals(true, response.getEnable());
        assertEquals("service-1", response.getServiceId());
        assertEquals("Service One", response.getServiceName());
        verify(redisSettingsSyncService).publishPermissionRuntimeChange(any(AccessPermission.class), eq("PERMISSION_CHANGED"), eq(null));
        verify(redisSettingsSyncService).syncInboundToRedis(endpoint);
        verify(redisSettingsSyncService).syncRuntimeSnapshotOfService("service-1");
    }

    @Test
    void createPermission_shouldPersistExplicitDisabledPermission() {
        AccessPermissionService service = newService();
        when(clientRepository.findById("client-1")).thenReturn(Optional.of(client("client-1", ClientStatus.ACTIVE)));
        when(inboundEndpointRepository.findById("endpoint-1")).thenReturn(Optional.of(endpoint("endpoint-1", "service-1")));
        when(accessPermissionRepository.save(any(AccessPermission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccessPermissionResponse response = service.createPermission(createRequest("client-1", "endpoint-1", false), authentication);

        assertEquals(false, response.getEnable());
    }

    @Test
    void createPermission_shouldRejectDuplicateClientEndpointPair() {
        AccessPermissionService service = newService();
        when(clientRepository.findById("client-1")).thenReturn(Optional.of(client("client-1", ClientStatus.ACTIVE)));
        when(inboundEndpointRepository.findById("endpoint-1")).thenReturn(Optional.of(endpoint("endpoint-1", "service-1")));
        when(accessPermissionRepository.existsByClientIdAndInboundEndpointId("client-1", "endpoint-1")).thenReturn(true);

        assertThrows(AppException.class, () -> service.createPermission(createRequest("client-1", "endpoint-1", true), authentication));
    }

    @Test
    void createPermission_shouldRejectMissingClientOrEndpoint() {
        AccessPermissionService service = newService();
        when(clientRepository.findById("client-1")).thenReturn(Optional.empty());
        assertThrows(AppException.class, () -> service.createPermission(createRequest("client-1", "endpoint-1", true), authentication));

        when(clientRepository.findById("client-1")).thenReturn(Optional.of(client("client-1", ClientStatus.ACTIVE)));
        when(inboundEndpointRepository.findById("endpoint-1")).thenReturn(Optional.empty());
        assertThrows(AppException.class, () -> service.createPermission(createRequest("client-1", "endpoint-1", true), authentication));
    }

    @Test
    void updatePermission_shouldToggleEnable() {
        AccessPermissionService service = newService();
        AccessPermission permission = permission("permission-1", true);
        when(accessPermissionRepository.findByIdWithClientAndEndpoint("permission-1")).thenReturn(Optional.of(permission));
        when(accessPermissionRepository.save(any(AccessPermission.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AccessPermissionUpdateRequest request = new AccessPermissionUpdateRequest();
        request.setEnable(false);

        AccessPermissionResponse response = service.updatePermission("permission-1", request, authentication);

        assertEquals(false, response.getEnable());
        assertEquals("service-1", response.getServiceId());
        assertEquals("Service One", response.getServiceName());
        verify(redisSettingsSyncService).publishPermissionRuntimeChange(permission, "PERMISSION_DISABLED", "DISABLED");
        verify(redisSettingsSyncService).syncInboundToRedis(permission.getInboundEndpoint());
        verify(redisSettingsSyncService).syncRuntimeSnapshotOfService("service-1");
    }

    @Test
    void deletePermission_shouldDeleteExistingPermission() {
        AccessPermissionService service = newService();
        AccessPermission permission = permission("permission-1", true);
        when(accessPermissionRepository.findByIdWithClientAndEndpoint("permission-1")).thenReturn(Optional.of(permission));

        service.deletePermission("permission-1", authentication);

        verify(accessPermissionRepository).delete(permission);
        verify(redisSettingsSyncService).publishPermissionRuntimeChange(permission, "PERMISSION_DELETED", "DELETED");
        verify(redisSettingsSyncService).syncInboundToRedis(permission.getInboundEndpoint());
        verify(redisSettingsSyncService).syncRuntimeSnapshotOfService("service-1");
    }

    @Test
    void listPermissions_shouldNotFilterByEnableWhenEnableIsOmitted() {
        AccessPermissionService service = newService();
        when(accessPermissionRepository.search(isNull(), isNull(), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(permission("permission-1", true), permission("permission-2", false))));

        var response = service.listPermissions(null, null, null, null, PageRequest.of(0, 20));

        assertEquals(2, response.getTotalElements());
        verify(accessPermissionRepository).search(isNull(), isNull(), isNull(), isNull(), any());
    }

    @Test
    void listPermissions_shouldPassEnableFilterWhenProvided() {
        AccessPermissionService service = newService();
        when(accessPermissionRepository.search(isNull(), isNull(), eq(false), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(permission("permission-2", false))));

        var response = service.listPermissions(null, null, false, null, PageRequest.of(0, 20));

        assertEquals(1, response.getTotalElements());
        assertEquals(false, response.getContent().get(0).getEnable());
    }

    private AccessPermissionService newService() {
        return new AccessPermissionService(accessPermissionRepository,
                clientRepository,
                inboundEndpointRepository,
                auditLogRepository,
                redisSettingsSyncService,
                new ObjectMapper());
    }

    private AccessPermissionCreateRequest createRequest(String clientId, String inboundEndpointId, Boolean enable) {
        AccessPermissionCreateRequest request = new AccessPermissionCreateRequest();
        request.setClientId(clientId);
        request.setInboundEndpointId(inboundEndpointId);
        request.setEnable(enable);
        return request;
    }

    private AccessPermission permission(String id, boolean enable) {
        AccessPermission permission = new AccessPermission();
        permission.setId(id);
        permission.setClient(client("client-1", ClientStatus.ACTIVE));
        permission.setInboundEndpoint(endpoint("endpoint-1", "service-1"));
        permission.setEnable(enable);
        return permission;
    }

    private Client client(String id, ClientStatus status) {
        Client client = new Client();
        client.setId(id);
        client.setName("Client One");
        client.setClientKey("client-key-1");
        client.setStatus(status);
        return client;
    }

    private InboundEndpoint endpoint(String id, String serviceId) {
        SecureService secureService = new SecureService();
        secureService.setId(serviceId);
        secureService.setName("Service One");
        InboundEndpoint endpoint = new InboundEndpoint();
        endpoint.setId(id);
        endpoint.setName("Endpoint One");
        endpoint.setPath("/api/one");
        endpoint.setSecureService(secureService);
        return endpoint;
    }
}
