package vdt.mini.management_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import vdt.mini.management_service.entity.AccessPermission;
import vdt.mini.management_service.entity.Client;
import vdt.mini.management_service.entity.InboundAccessRule;
import vdt.mini.management_service.entity.InboundEndpoint;
import vdt.mini.management_service.entity.SecureService;
import vdt.mini.management_service.repository.AccessPermissionRepository;
import vdt.mini.management_service.repository.AuthConfigRepository;
import vdt.mini.management_service.repository.InboundEndpointRepository;
import vdt.mini.management_service.repository.OutboundEndpointRepository;
import vdt.mini.management_service.util.enums.AccessRuleType;
import vdt.mini.management_service.util.enums.AccessRuleValueType;
import vdt.mini.management_service.util.enums.EndpointProtocol;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisSettingsSyncServiceTest {
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private AuthConfigRepository authConfigRepository;
    @Mock
    private AccessPermissionRepository accessPermissionRepository;
    @Mock
    private InboundEndpointRepository inboundEndpointRepository;
    @Mock
    private OutboundEndpointRepository outboundEndpointRepository;

    @Test
    void syncInboundToRedis_shouldIncludeOnlyEnabledRulesAndPermissions() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        RedisSettingsSyncService service = new RedisSettingsSyncService(redisTemplate,
                objectMapper,
                authConfigRepository,
                accessPermissionRepository,
                inboundEndpointRepository,
                outboundEndpointRepository);
        InboundEndpoint endpoint = endpoint();
        endpoint.setAccessRules(java.util.Set.of(
                rule("enabled-rule", true, null),
                rule("disabled-rule", false, null),
                rule("expired-rule", true, LocalDateTime.now().minusDays(1))));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(authConfigRepository.findEnabledByServiceScope("service-1")).thenReturn(List.of());
        when(accessPermissionRepository.findEnabledByInboundEndpointId("endpoint-1"))
                .thenReturn(List.of(permission("permission-1")));

        service.syncInboundToRedis(endpoint);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq("security:config:inbound:endpoint-1"), jsonCaptor.capture());
        JsonNode payload = objectMapper.readTree(jsonCaptor.getValue());
        assertEquals(1, payload.get("accessRules").size());
        assertEquals("enabled-rule", payload.get("accessRules").get(0).get("value").asText());
        assertEquals(1, payload.get("permissions").size());
        assertEquals("permission-1", payload.get("permissions").get(0).get("permissionId").asText());
        verify(redisTemplate).convertAndSend(eq("security:settings:service-1"), anyString());
    }

    private InboundEndpoint endpoint() {
        SecureService secureService = new SecureService();
        secureService.setId("service-1");
        InboundEndpoint endpoint = new InboundEndpoint();
        endpoint.setId("endpoint-1");
        endpoint.setName("Endpoint One");
        endpoint.setPath("/api/one");
        endpoint.setProtocol(EndpointProtocol.HTTP);
        endpoint.setEnabled(true);
        endpoint.setSecureService(secureService);
        return endpoint;
    }

    private InboundAccessRule rule(String value, boolean enable, LocalDateTime expiresAt) {
        InboundAccessRule rule = new InboundAccessRule();
        rule.setId(value);
        rule.setType(AccessRuleType.BLACKLIST);
        rule.setValueType(AccessRuleValueType.IP);
        rule.setValue(value);
        rule.setTemporary(expiresAt != null);
        rule.setExpiresAt(expiresAt);
        rule.setEnable(enable);
        return rule;
    }

    private AccessPermission permission(String id) {
        Client client = new Client();
        client.setId("client-1");
        client.setClientKey("client-key-1");
        AccessPermission permission = new AccessPermission();
        permission.setId(id);
        permission.setClient(client);
        permission.setInboundEndpoint(endpoint());
        permission.setEnable(true);
        return permission;
    }
}
