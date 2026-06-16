package vdt.mini.management_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vdt.mini.management_service.dto.request.AccessRuleCreateRequest;
import vdt.mini.management_service.dto.request.AccessRuleUpdateRequest;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
    void createAccessRule_shouldPersistAuditAndRegisterAfterCommitEvent() {
        TransactionSynchronizationManager.initSynchronization();
        AccessRuleService service = newService();
        vdt.mini.management_service.entity.SecureService secureService = new vdt.mini.management_service.entity.SecureService();
        secureService.setId("service-1");
        secureService.setName("Service One");
        InboundEndpoint endpoint = new InboundEndpoint();
        endpoint.setId("endpoint-1");
        endpoint.setSecureService(secureService);
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
        assertEquals("service-1", response.getServiceId());
        assertEquals("Service One", response.getServiceName());
        assertEquals(AccessRuleType.BLACKLIST, response.getType());
        assertEquals(AccessRuleValueType.IP, response.getValueType());
        assertTrue(response.getTemporary());
        assertTrue(response.getEnable());
        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        assertEquals("ACCESS_RULE_CREATED", auditCaptor.getValue().getAction());
        assertEquals("ACCESS_RULE", auditCaptor.getValue().getEntityType());
        assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());
    }

    @Test
    void createAccessRule_shouldPersistExplicitDisabledRule() {
        AccessRuleService service = newService();
        InboundEndpoint endpoint = new InboundEndpoint();
        endpoint.setId("endpoint-1");
        when(inboundEndpointRepository.findById("endpoint-1")).thenReturn(Optional.of(endpoint));
        when(accessRuleRepository.save(any(InboundAccessRule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccessRuleCreateRequest request = new AccessRuleCreateRequest();
        request.setType("BLACKLIST");
        request.setValueType("IP");
        request.setValue("192.168.1.10");
        request.setEnable(false);

        AccessRuleResponse response = service.createAccessRule("endpoint-1", request, authentication);

        assertEquals(false, response.getEnable());
    }

    @Test
    void listAccessRules_shouldPassNullEnableFilterAndReturnEnabledAndDisabledRules() {
        AccessRuleService service = newService();
        InboundEndpoint endpoint = new InboundEndpoint();
        endpoint.setId("endpoint-1");
        InboundAccessRule enabledRule = rule("rule-1", endpoint, true);
        InboundAccessRule disabledRule = rule("rule-2", endpoint, false);
        when(inboundEndpointRepository.existsById("endpoint-1")).thenReturn(true);
        when(accessRuleRepository.search(eq("endpoint-1"), isNull(), isNull(), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(enabledRule, disabledRule)));

        var response = service.listAccessRules("endpoint-1", null, null, null, null, PageRequest.of(0, 20));

        assertEquals(2, response.getTotalElements());
        verify(accessRuleRepository).search(eq("endpoint-1"), isNull(), isNull(), isNull(), isNull(), any());
    }

    @Test
    void listAccessRules_shouldPassEnableFilterWhenProvided() {
        AccessRuleService service = newService();
        InboundEndpoint endpoint = new InboundEndpoint();
        endpoint.setId("endpoint-1");
        when(inboundEndpointRepository.existsById("endpoint-1")).thenReturn(true);
        when(accessRuleRepository.search(eq("endpoint-1"), isNull(), isNull(), eq(false), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(rule("rule-2", endpoint, false))));

        var response = service.listAccessRules("endpoint-1", null, null, false, null, PageRequest.of(0, 20));

        assertEquals(1, response.getTotalElements());
        assertEquals(false, response.getContent().get(0).getEnable());
    }

    @Test
    void updateAccessRule_shouldToggleEnableAndRegisterRedisSyncAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        AccessRuleService service = newService();
        vdt.mini.management_service.entity.SecureService secureService = new vdt.mini.management_service.entity.SecureService();
        secureService.setId("service-1");
        secureService.setName("Service One");
        InboundEndpoint endpoint = new InboundEndpoint();
        endpoint.setId("endpoint-1");
        endpoint.setSecureService(secureService);
        InboundAccessRule rule = rule("rule-1", endpoint, true);
        when(accessRuleRepository.findById("rule-1")).thenReturn(Optional.of(rule));
        when(accessRuleRepository.save(any(InboundAccessRule.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AccessRuleUpdateRequest request = new AccessRuleUpdateRequest();
        request.setEnable(false);

        AccessRuleResponse response = service.updateAccessRule("rule-1", request, authentication);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization -> TransactionSynchronization.afterCommit());

        assertEquals(false, response.getEnable());
        assertEquals("service-1", response.getServiceId());
        assertEquals("Service One", response.getServiceName());
        verify(redisSettingsSyncService).syncInboundToRedis(endpoint);
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
    void createAccessRule_shouldAcceptHeaderValueType() {
        AccessRuleService service = newService();
        InboundEndpoint endpoint = new InboundEndpoint();
        endpoint.setId("endpoint-1");
        when(inboundEndpointRepository.findById("endpoint-1")).thenReturn(Optional.of(endpoint));
        when(accessRuleRepository.save(any(InboundAccessRule.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AccessRuleCreateRequest request = new AccessRuleCreateRequest();
        request.setType("WHITELIST");
        request.setValueType("HEADER");
        request.setValue("x-client");
        request.setTemporary(false);

        AccessRuleResponse response = service.createAccessRule("endpoint-1", request, authentication);

        assertEquals(AccessRuleValueType.HEADER, response.getValueType());
    }

    @Test
    void listAllAccessRules_shouldSearchAcrossEndpointsAndReturnEndpointMetadata() {
        AccessRuleService service = newService();
        InboundEndpoint endpoint = new InboundEndpoint();
        endpoint.setId("endpoint-1");
        endpoint.setName("Auth Token Endpoint");
        endpoint.setPath("/api/auth/token");
        when(accessRuleRepository.searchAll(eq(AccessRuleType.BLACKLIST), isNull(), eq("%auth%"), isNull(), isNull(), eq("%fraud%"), any()))
                .thenReturn(new PageImpl<>(List.of(rule("rule-1", endpoint, true))));

        var response = service.listAllAccessRules("BLACKLIST", null, "Auth", null, null, "Fraud", PageRequest.of(0, 20));

        assertEquals(1, response.getTotalElements());
        assertEquals("endpoint-1", response.getContent().get(0).getInboundEndpointId());
        assertEquals("Auth Token Endpoint", response.getContent().get(0).getInboundEndpointName());
        assertEquals("/api/auth/token", response.getContent().get(0).getInboundEndpointPath());
        verify(accessRuleRepository).searchAll(eq(AccessRuleType.BLACKLIST), isNull(), eq("%auth%"), isNull(), isNull(), eq("%fraud%"), any());
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
        request.setValueType("CLIENT_KEY");
        request.setValue("client-key-1");
        request.setTemporary(false);
        request.setExpiresAt(LocalDateTime.now().plusDays(1));

        AccessRuleResponse response = service.createAccessRule("endpoint-1", request, authentication);

        assertEquals(AccessRuleValueType.CLIENT_KEY, response.getValueType());
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
                redisSettingsSyncService,
                new ObjectMapper());
    }

    private InboundAccessRule rule(String id, InboundEndpoint endpoint, boolean enable) {
        InboundAccessRule rule = new InboundAccessRule();
        rule.setId(id);
        rule.setInboundEndpoint(endpoint);
        rule.setType(AccessRuleType.BLACKLIST);
        rule.setValueType(AccessRuleValueType.IP);
        rule.setValue("127.0.0.1");
        rule.setTemporary(false);
        rule.setEnable(enable);
        return rule;
    }
}
