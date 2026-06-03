package vdt.mini.management_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vdt.mini.management_service.dto.request.AccessRuleCreateRequest;
import vdt.mini.management_service.dto.response.AccessRuleResponse;
import vdt.mini.management_service.entity.AuditLog;
import vdt.mini.management_service.entity.InboundAccessRule;
import vdt.mini.management_service.entity.InboundEndpoint;
import vdt.mini.management_service.exception.AppException;
import vdt.mini.management_service.repository.AuditLogRepository;
import vdt.mini.management_service.repository.InboundAccessRuleRepository;
import vdt.mini.management_service.repository.InboundEndpointRepository;
import vdt.mini.management_service.util.enums.AccessRuleType;
import vdt.mini.management_service.util.enums.AccessRuleValueType;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessRuleServiceTest {
    @Mock
    private InboundAccessRuleRepository accessRuleRepository;

    @Mock
    private InboundEndpointRepository inboundEndpointRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private ClientSecurityEventPublisher eventPublisher;

    @Mock
    private Authentication authentication;

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void createAccessRule_shouldPersistAuditAndRegisterAfterCommitEvent() {
        TransactionSynchronizationManager.initSynchronization();
        AccessRuleService service = newService();
        InboundEndpoint endpoint = new InboundEndpoint();
        endpoint.setId("endpoint-1");
        when(inboundEndpointRepository.findById("endpoint-1")).thenReturn(Optional.of(endpoint));
        when(accessRuleRepository.save(any(InboundAccessRule.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(authentication.getName()).thenReturn("admin");

        AccessRuleCreateRequest request = new AccessRuleCreateRequest();
        request.setType("BLACKLIST");
        request.setValueType("IP");
        request.setValue("192.168.1.10");
        request.setTemporary(true);
        request.setExpiresAt(LocalDateTime.now().plusDays(1));
        request.setReason("Suspicious request volume");

        AccessRuleResponse response = service.createAccessRule("endpoint-1", request, authentication);

        assertNotNull(response.getId());
        assertEquals("endpoint-1", response.getInboundEndpointId());
        assertEquals(AccessRuleType.BLACKLIST, response.getType());
        assertEquals(AccessRuleValueType.IP, response.getValueType());
        assertTrue(response.getTemporary());
        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        assertEquals("ACCESS_RULE_CREATED", auditCaptor.getValue().getAction());
        assertEquals("ACCESS_RULE", auditCaptor.getValue().getEntityType());
        assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());
    }

    @Test
    void createAccessRule_shouldRejectTemporaryRuleWithoutExpiresAt() {
        AccessRuleService service = newService();
        AccessRuleCreateRequest request = new AccessRuleCreateRequest();
        request.setType("BLACKLIST");
        request.setValueType("IP");
        request.setValue("192.168.1.10");
        request.setTemporary(true);

        assertThrows(AppException.class, () -> service.createAccessRule("endpoint-1", request, authentication));
    }

    @Test
    void createAccessRule_shouldRejectUnsupportedValueType() {
        AccessRuleService service = newService();
        AccessRuleCreateRequest request = new AccessRuleCreateRequest();
        request.setType("WHITELIST");
        request.setValueType("HEADER");
        request.setValue("x-client");
        request.setTemporary(false);

        assertThrows(AppException.class, () -> service.createAccessRule("endpoint-1", request, authentication));
    }

    @Test
    void createAccessRule_shouldIgnoreExpiresAtForPermanentRule() {
        TransactionSynchronizationManager.initSynchronization();
        AccessRuleService service = newService();
        InboundEndpoint endpoint = new InboundEndpoint();
        endpoint.setId("endpoint-1");
        when(inboundEndpointRepository.findById("endpoint-1")).thenReturn(Optional.of(endpoint));
        when(accessRuleRepository.save(any(InboundAccessRule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccessRuleCreateRequest request = new AccessRuleCreateRequest();
        request.setType("WHITELIST");
        request.setValueType("CLIENT_ID");
        request.setValue("client-1");
        request.setTemporary(false);
        request.setExpiresAt(LocalDateTime.now().plusDays(1));

        AccessRuleResponse response = service.createAccessRule("endpoint-1", request, authentication);

        assertEquals(AccessRuleValueType.CLIENT_ID, response.getValueType());
        assertNull(response.getExpiresAt());
    }

    @Test
    void deleteAccessRule_shouldDeleteAuditAndRegisterAfterCommitEvent() {
        TransactionSynchronizationManager.initSynchronization();
        AccessRuleService service = newService();
        InboundEndpoint endpoint = new InboundEndpoint();
        endpoint.setId("endpoint-1");
        InboundAccessRule rule = new InboundAccessRule();
        rule.setId("rule-1");
        rule.setInboundEndpoint(endpoint);
        when(accessRuleRepository.findById("rule-1")).thenReturn(Optional.of(rule));
        when(authentication.getName()).thenReturn("admin");

        service.deleteAccessRule("rule-1", authentication);

        verify(accessRuleRepository).delete(rule);
        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        assertEquals("ACCESS_RULE_DELETED", auditCaptor.getValue().getAction());
        assertEquals("rule-1", auditCaptor.getValue().getEntityId());
        assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());
    }

    private AccessRuleService newService() {
        return new AccessRuleService(accessRuleRepository,
                inboundEndpointRepository,
                auditLogRepository,
                eventPublisher,
                new ObjectMapper());
    }
}
