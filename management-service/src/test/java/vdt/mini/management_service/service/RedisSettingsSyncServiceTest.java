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
import vdt.mini.management_service.entity.AuthConfig;
import vdt.mini.management_service.entity.Client;
import vdt.mini.management_service.entity.InboundAccessRule;
import vdt.mini.management_service.entity.InboundEndpoint;
import vdt.mini.management_service.entity.OutboundEndpoint;
import vdt.mini.management_service.entity.SecureService;
import vdt.mini.management_service.repository.AccessPermissionRepository;
import vdt.mini.management_service.repository.AuthConfigRepository;
import vdt.mini.management_service.repository.ClientRepository;
import vdt.mini.management_service.repository.InboundEndpointRepository;
import vdt.mini.management_service.repository.OutboundEndpointRepository;
import vdt.mini.management_service.util.enums.AccessRuleType;
import vdt.mini.management_service.util.enums.AccessRuleValueType;
import vdt.mini.management_service.util.enums.EndpointProtocol;
import vdt.mini.management_service.util.enums.AuthType;
import vdt.mini.management_service.util.enums.ClientStatus;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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
    private ClientRepository clientRepository;
    @Mock
    private InboundEndpointRepository inboundEndpointRepository;
    @Mock
    private OutboundEndpointRepository outboundEndpointRepository;
    @Mock
    private SecretCipherService secretCipherService;

    @Test
    void syncInboundToRedis_shouldIncludeOnlyEnabledRulesAndPermissions() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        RedisSettingsSyncService service = new RedisSettingsSyncService(redisTemplate,
                objectMapper,
                authConfigRepository,
                accessPermissionRepository,
                inboundEndpointRepository,
                outboundEndpointRepository,
                secretCipherService);
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
        JsonNode inboundPayload = objectMapper.readTree(jsonCaptor.getValue());
        assertEquals(1, inboundPayload.get("accessRules").size());
        assertEquals("enabled-rule", inboundPayload.get("accessRules").get(0).get("value").asText());
        assertEquals(1, inboundPayload.get("permissions").size());
        assertEquals("permission-1", inboundPayload.get("permissions").get(0).get("permissionId").asText());
        verify(redisTemplate).convertAndSend(eq("security:settings:service-1"), anyString());
        verify(redisTemplate, never()).convertAndSend(eq("security:runtime:v1:service:service-1:events"),
                argThat((String payload) -> payload != null && payload.contains("INBOUND_SETTINGS_CHANGED")));
    }

    @Test
    void syncInboundToRedis_shouldDecryptHmacClientKey_andKeepApiKeyNull() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        RedisSettingsSyncService service = new RedisSettingsSyncService(redisTemplate,
                objectMapper,
                authConfigRepository,
                accessPermissionRepository,
                inboundEndpointRepository,
                outboundEndpointRepository,
                secretCipherService);
        InboundEndpoint endpoint = endpoint();
        AuthConfig hmac = authConfig("auth-hmac", AuthType.HMAC_SIGNATURE, "hmac-ref", "ciphertext");
        AuthConfig apiKey = authConfig("auth-api", AuthType.API_KEY, "api-ref", null);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(authConfigRepository.findEnabledByServiceScope("service-1")).thenReturn(List.of(hmac, apiKey));
        when(accessPermissionRepository.findEnabledByInboundEndpointId("endpoint-1")).thenReturn(List.of());
        when(secretCipherService.decrypt("ciphertext")).thenReturn("hmac-secret");

        service.syncInboundToRedis(endpoint);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq("security:config:inbound:endpoint-1"), jsonCaptor.capture());
        JsonNode authConfigs = objectMapper.readTree(jsonCaptor.getValue()).get("authConfigs");
        assertEquals("hmac-secret", authConfigs.get(0).get("clientKey").asText());
        assertEquals("API_KEY", authConfigs.get(1).get("type").asText());
        assertEquals(true, authConfigs.get(1).get("clientKey").isNull());
    }

    @Test
    void syncOutboundToRedis_shouldPublishOnlyLegacySettingsMessage() {
        ObjectMapper objectMapper = new ObjectMapper();
        RedisSettingsSyncService service = new RedisSettingsSyncService(redisTemplate,
                objectMapper,
                authConfigRepository,
                accessPermissionRepository,
                inboundEndpointRepository,
                outboundEndpointRepository,
                secretCipherService);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        service.syncOutboundToRedis(outboundEndpoint());

        verify(redisTemplate).convertAndSend(eq("security:settings:service-1"), anyString());
        verify(redisTemplate, never()).convertAndSend(eq("security:runtime:v1:service:service-1:events"),
                argThat((String payload) -> payload != null && payload.contains("OUTBOUND_SETTINGS_CHANGED")));
    }

    @Test
    void syncRuntimeSnapshotOfService_shouldPropagateCredentialHashAndKeepSnapshotEvent() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        RedisSettingsSyncService service = new RedisSettingsSyncService(redisTemplate,
                objectMapper,
                authConfigRepository,
                accessPermissionRepository,
                clientRepository,
                inboundEndpointRepository,
                outboundEndpointRepository,
                secretCipherService);
        AuthConfig apiKey = authConfig("auth-api", AuthType.API_KEY, "api-ref", null);
        apiKey.setCredentialHash("api-hash");
        apiKey.setService(secureService());
        AuthConfig hmac = authConfig("auth-hmac", AuthType.HMAC_SIGNATURE, "hmac-ref", "ciphertext");
        hmac.setCredentialHash("hmac-hash");
        hmac.setService(secureService());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(inboundEndpointRepository.findAllBySecureServiceId("service-1")).thenReturn(List.of(endpoint()));
        when(outboundEndpointRepository.findAllBySecureServiceId("service-1")).thenReturn(List.of(outboundEndpoint()));
        when(clientRepository.findRuntimeClientsByServiceId("service-1")).thenReturn(List.of(client()));
        when(authConfigRepository.findRuntimeByServiceId("service-1")).thenReturn(List.of(apiKey, hmac));
        when(accessPermissionRepository.findRuntimeByServiceId("service-1")).thenReturn(List.of());
        when(secretCipherService.decrypt("ciphertext")).thenReturn("hmac-secret");

        service.syncRuntimeSnapshotOfService("service-1");

        ArgumentCaptor<String> authSnapshotCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq("security:runtime:v1:service:service-1:auth-configs"), authSnapshotCaptor.capture());
        JsonNode authConfigs = objectMapper.readTree(authSnapshotCaptor.getValue()).get("authConfigs");
        assertEquals("api-hash", authConfigs.get(0).get("credentialHash").asText());
        assertEquals(true, authConfigs.get(0).get("secretKey").isNull());
        assertEquals("hmac-hash", authConfigs.get(1).get("credentialHash").asText());
        assertEquals("hmac-secret", authConfigs.get(1).get("secretKey").asText());
        verify(redisTemplate).convertAndSend(eq("security:runtime:v1:service:service-1:events"),
                argThat((String payload) -> payload != null && payload.contains("SERVICE_SNAPSHOT_REFRESHED")));
    }

    @Test
    void syncInboundToRedis_shouldContinue_whenHmacCiphertextMissingOrMalformed() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        RedisSettingsSyncService service = new RedisSettingsSyncService(redisTemplate,
                objectMapper,
                authConfigRepository,
                accessPermissionRepository,
                inboundEndpointRepository,
                outboundEndpointRepository,
                secretCipherService);
        InboundEndpoint endpoint = endpoint();
        AuthConfig missing = authConfig("auth-missing", AuthType.HMAC_SIGNATURE, "missing-ref", null);
        AuthConfig malformed = authConfig("auth-malformed", AuthType.HMAC_SIGNATURE, "malformed-ref", "malformed");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(authConfigRepository.findEnabledByServiceScope("service-1")).thenReturn(List.of(missing, malformed));
        when(accessPermissionRepository.findEnabledByInboundEndpointId("endpoint-1")).thenReturn(List.of());
        doThrow(new IllegalStateException("bad ciphertext")).when(secretCipherService).decrypt("malformed");

        service.syncInboundToRedis(endpoint);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq("security:config:inbound:endpoint-1"), jsonCaptor.capture());
        JsonNode authConfigs = objectMapper.readTree(jsonCaptor.getValue()).get("authConfigs");
        assertFalse(authConfigs.get(0).get("clientKey").isTextual());
        assertFalse(authConfigs.get(1).get("clientKey").isTextual());
    }

    private InboundEndpoint endpoint() {
        InboundEndpoint endpoint = new InboundEndpoint();
        endpoint.setId("endpoint-1");
        endpoint.setName("Endpoint One");
        endpoint.setPath("/api/one");
        endpoint.setProtocol(EndpointProtocol.HTTP);
        endpoint.setEnabled(true);
        endpoint.setSecureService(secureService());
        return endpoint;
    }

    private OutboundEndpoint outboundEndpoint() {
        OutboundEndpoint endpoint = new OutboundEndpoint();
        endpoint.setId("outbound-1");
        endpoint.setName("Outbound One");
        endpoint.setTargetUrl("https://example.test/api");
        endpoint.setProtocol(EndpointProtocol.HTTP);
        endpoint.setEnabled(true);
        endpoint.setSecureService(secureService());
        return endpoint;
    }

    private SecureService secureService() {
        SecureService secureService = new SecureService();
        secureService.setId("service-1");
        return secureService;
    }

    private Client client() {
        Client client = new Client();
        client.setId("client-1");
        client.setClientKey("client-key-1");
        client.setName("Client One");
        client.setStatus(ClientStatus.ACTIVE);
        return client;
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

    private AuthConfig authConfig(String id, AuthType type, String secretRef, String secretCiphertext) {
        AuthConfig authConfig = new AuthConfig();
        authConfig.setId(id);
        authConfig.setClient(client());
        authConfig.setType(type);
        authConfig.setSecretRef(secretRef);
        authConfig.setAlgorithm(type == AuthType.HMAC_SIGNATURE ? "HmacSHA256" : null);
        authConfig.setSecretCiphertext(secretCiphertext);
        authConfig.setEnabled(true);
        return authConfig;
    }
}
