package vdt.mini.shared_lib.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import vdt.mini.shared_lib.document.AccessPermissionDTO;
import vdt.mini.shared_lib.document.AccessRuleDTO;
import vdt.mini.shared_lib.document.AuthConfigRuntimeDTO;
import vdt.mini.shared_lib.document.ClientRuntimeDTO;
import vdt.mini.shared_lib.document.InboundSettingsDTO;
import vdt.mini.shared_lib.document.PermissionRuntimeDTO;
import vdt.mini.shared_lib.document.RuntimeChangePayloadDTO;
import vdt.mini.shared_lib.document.RuntimeTombstoneDTO;
import vdt.mini.shared_lib.document.SecurityRuntimeChangeMessage;
import vdt.mini.shared_lib.enums.SecurityErrorCode;
import vdt.mini.shared_lib.mq.MqSecurityHeaders;
import vdt.mini.shared_lib.mq.MqSecurityRequest;
import vdt.mini.shared_lib.service.EndpointRegistry;
import vdt.mini.shared_lib.service.RedisSecurityRuntimeKeys;
import vdt.mini.shared_lib.service.SecuritySettingsStore;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InboundSecurityDecisionServiceMqTest {
    private static final String SERVICE_ID = "service-1";
    private static final String ENDPOINT_ID = "endpoint-1";
    private static final String CLIENT_ID = "client-1";
    private static final String CLIENT_KEY = "client-key-1";
    private static final String API_KEY = "api-key-1";
    private static final String TOPIC = "user.created";

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private ObjectMapper objectMapper;
    private SecuritySettingsStore settingsStore;
    private InboundSecurityDecisionService decisionService;
    private EndpointRegistry.InboundMqEndpoint endpoint;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        settingsStore = new SecuritySettingsStore(redisTemplate, objectMapper);
        decisionService = new InboundSecurityDecisionService(settingsStore, new NonceReplayStore(redisTemplate), true);
        endpoint = new EndpointRegistry.InboundMqEndpoint(ENDPOINT_ID, "User Created", TOPIC, "MQ");
    }

    @Test
    void decide_shouldDenyMissingListenerAndMissingHeaders() throws Exception {
        SecurityDecision missingEndpoint = decisionService.decide(request(headers(CLIENT_KEY), "payload"), null, context());
        assertThat(missingEndpoint.errorCode()).isEqualTo(SecurityErrorCode.LISTENER_NOT_REGISTERED);

        loadSettings(settings(settings -> settings.setPermissions(List.of())));
        SecurityDecision missingAuth = decisionService.decide(request(null, "payload"), endpoint, context());
        assertThat(missingAuth.errorCode()).isEqualTo(SecurityErrorCode.AUTH_MISSING);
    }

    @Test
    void decide_shouldDenyDisabledProtocolSizeAndAccessRules() throws Exception {
        assertDenied(settings(settings -> settings.setEnabled(false)), SecurityErrorCode.ENDPOINT_DISABLED);
        assertDenied(settings(settings -> settings.setProtocol("HTTP")), SecurityErrorCode.INVALID_MESSAGE);
        assertDenied(settings(settings -> settings.setRequestSizeLimitKb(1)), SecurityErrorCode.REQUEST_SIZE_EXCEEDED,
                request(headers(CLIENT_KEY), "x".repeat(2049)));
        assertDenied(settings(settings -> settings.setAccessRules(List.of(new AccessRuleDTO("BLACKLIST", "TOPIC", TOPIC, false, null)))),
                SecurityErrorCode.BLACKLISTED);
    }

    @Test
    void decide_shouldAllowWhitelistedMessageWithoutAuth() throws Exception {
        loadSettings(settings(settings -> settings.setAccessRules(List.of(new AccessRuleDTO("WHITELIST", "TOPIC", TOPIC, false, null)))));

        SecurityDecision decision = decisionService.decide(request(null, "payload"), endpoint, context());

        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void decide_shouldValidateApiKeyPermissionAndClientRuntime() throws Exception {
        loadSettings(settings(settings -> settings.setPermissions(List.of(new AccessPermissionDTO("permission-1", CLIENT_ID, CLIENT_KEY, ENDPOINT_ID)))));
        loadRuntime(true, "API_KEY", null, sha256(API_KEY));

        SecurityRequestContext context = context();
        SecurityDecision decision = decisionService.decide(request(headers(CLIENT_KEY, API_KEY), "payload"), endpoint, context);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.clientId()).isEqualTo(CLIENT_ID);
        assertThat(context.getMethod()).isEqualTo("POST");
        assertThat(context.getAuthType()).isEqualTo("API_KEY");
        assertThat(context.getDenyReason()).isNull();
    }

    @Test
    void decide_shouldDenyWhenRuntimePermissionDisabled_evenIfSettingsStillContainsPermission() throws Exception {
        loadSettings(settings(settings -> settings.setPermissions(List.of(new AccessPermissionDTO("permission-1", CLIENT_ID, CLIENT_KEY, ENDPOINT_ID)))));
        loadRuntime(true, "API_KEY", null, sha256(API_KEY), false);

        SecurityDecision decision = decisionService.decide(request(headers(CLIENT_KEY, API_KEY), "payload"), endpoint, context());

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.errorCode()).isEqualTo(SecurityErrorCode.WHITELIST_NOT_MATCHED);
    }

    @Test
    void decide_shouldDenyWhenRuntimePermissionDeleted_evenIfSettingsStillContainsPermission() throws Exception {
        loadSettings(settings(settings -> settings.setPermissions(List.of(new AccessPermissionDTO("permission-1", CLIENT_ID, CLIENT_KEY, ENDPOINT_ID)))));
        loadRuntime(true, "API_KEY", null, sha256(API_KEY));
        deleteRuntimePermission();

        SecurityDecision decision = decisionService.decide(request(headers(CLIENT_KEY, API_KEY), "payload"), endpoint, context());

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.errorCode()).isEqualTo(SecurityErrorCode.WHITELIST_NOT_MATCHED);
    }

    @Test
    void decide_shouldValidateHmacAndRejectNonceReplay() throws Exception {
        loadSettings(settings(settings -> settings.setPermissions(List.of(new AccessPermissionDTO("permission-1", CLIENT_ID, CLIENT_KEY, ENDPOINT_ID)))));
        loadRuntime(true, "HMAC_SIGNATURE", "secret", null);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("security:runtime:nonce:mq:in:service-1:endpoint-1:client-key-1:nonce-1"), eq("1"), any()))
                .thenReturn(true, false);
        long timestamp = Instant.now().toEpochMilli();
        String payload = "payload";
        MqSecurityHeaders headers = new MqSecurityHeaders(CLIENT_KEY, null,
                signature("MQ\n" + TOPIC + "\n" + timestamp + "\nnonce-1\n" + sha256(payload), "secret"),
                String.valueOf(timestamp), "nonce-1", "corr", "trace");

        assertThat(decisionService.decide(request(headers, payload), endpoint, context()).allowed()).isTrue();
        SecurityRequestContext replayContext = context();
        SecurityDecision replayDecision = decisionService.decide(request(headers, payload), endpoint, replayContext);
        assertThat(replayDecision.errorCode()).isEqualTo(SecurityErrorCode.HMAC_INVALID);
        assertThat(replayContext.getAuthType()).isEqualTo("HMAC_SIGNATURE");
        assertThat(replayContext.getDenyReason()).isEqualTo("Invalid HMAC signature");
    }

    private void assertDenied(InboundSettingsDTO settings, SecurityErrorCode expected) throws Exception {
        assertDenied(settings, expected, request(headers(CLIENT_KEY), "payload"));
    }

    private void assertDenied(InboundSettingsDTO settings, SecurityErrorCode expected, MqSecurityRequest request) throws Exception {
        loadSettings(settings);
        SecurityDecision decision = decisionService.decide(request, endpoint, context());

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.errorCode()).isEqualTo(expected);
    }

    private void loadSettings(InboundSettingsDTO settings) throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(RedisSecurityRuntimeKeys.inboundSettings(ENDPOINT_ID))).thenReturn(objectMapper.writeValueAsString(settings));
        settingsStore.pollFromRedis(List.of(ENDPOINT_ID), List.of());
    }

    private void loadRuntime(boolean includePermission, String authType, String secretKey, String credentialHash) {
        loadRuntime(includePermission, authType, secretKey, credentialHash, true);
    }

    private void loadRuntime(boolean includePermission, String authType, String secretKey, String credentialHash,
                             boolean permissionEnabled) {
        AuthConfigRuntimeDTO auth = new AuthConfigRuntimeDTO("auth-1", SERVICE_ID, CLIENT_ID, CLIENT_KEY, authType, null,
                credentialHash, "HmacSHA256", null, secretKey, null, true, "ACTIVE", 1L);
        PermissionRuntimeDTO permission = new PermissionRuntimeDTO("permission-1", SERVICE_ID, ENDPOINT_ID, CLIENT_ID, CLIENT_KEY, permissionEnabled, "ACTIVE", 1L);
        settingsStore.onRuntimeChange(new SecurityRuntimeChangeMessage("event-1", "CLIENT_CHANGED", SERVICE_ID, ENDPOINT_ID, CLIENT_ID,
                "auth-1", "permission-1", List.of(), System.nanoTime(), "2026-06-09T00:00:00",
                new RuntimeChangePayloadDTO(client(), List.of(auth), includePermission ? List.of(permission) : List.of(), List.of())));
    }

    private void deleteRuntimePermission() {
        RuntimeTombstoneDTO tombstone = new RuntimeTombstoneDTO("PERMISSION", SERVICE_ID, ENDPOINT_ID, CLIENT_ID,
                null, "permission-1", "deleted");
        settingsStore.onRuntimeChange(new SecurityRuntimeChangeMessage("event-2", "PERMISSION_DELETED", SERVICE_ID, ENDPOINT_ID, CLIENT_ID,
                null, "permission-1", List.of(), System.nanoTime(), "2026-06-09T00:00:01",
                new RuntimeChangePayloadDTO(null, List.of(), List.of(), List.of(tombstone))));
    }

    private ClientRuntimeDTO client() {
        return new ClientRuntimeDTO(CLIENT_ID, CLIENT_KEY, "Client", "ACTIVE", true, true, null, null, 1L);
    }

    private SecurityRequestContext context() {
        SecurityRequestContext context = new SecurityRequestContext();
        context.setServiceId(SERVICE_ID);
        context.setProtocol("MQ");
        context.setTopic(TOPIC);
        return context;
    }

    private MqSecurityRequest request(MqSecurityHeaders headers, String payload) {
        return new MqSecurityRequest(TOPIC, "key", payload, headers, payload == null ? 0L : payload.getBytes(StandardCharsets.UTF_8).length);
    }

    private MqSecurityHeaders headers(String clientKey) {
        return headers(clientKey, null);
    }

    private MqSecurityHeaders headers(String clientKey, String apiKey) {
        return new MqSecurityHeaders(clientKey, apiKey, null, null, null, "corr", "trace");
    }

    private InboundSettingsDTO settings(SettingsCustomizer customizer) {
        InboundSettingsDTO settings = new InboundSettingsDTO(ENDPOINT_ID, "User Created", null, TOPIC, "POST", "MQ", true,
                "ACTIVE", "ACTIVE", true, null, null, null, null, null, null, 14, null, null, null,
                List.of(), List.of(), List.of());
        customizer.customize(settings);
        return settings;
    }

    private String signature(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    private String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    @FunctionalInterface
    private interface SettingsCustomizer {
        void customize(InboundSettingsDTO settings);
    }
}
