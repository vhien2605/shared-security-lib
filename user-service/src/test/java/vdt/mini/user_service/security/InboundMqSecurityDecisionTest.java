package vdt.mini.user_service.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
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
import vdt.mini.shared_lib.document.InboundEndpointDTO;
import vdt.mini.shared_lib.document.InboundSettingsDTO;
import vdt.mini.shared_lib.document.PermissionRuntimeDTO;
import vdt.mini.shared_lib.document.RuntimeChangePayloadDTO;
import vdt.mini.shared_lib.document.SecurityRuntimeChangeMessage;
import vdt.mini.shared_lib.enums.SecurityErrorCode;
import vdt.mini.shared_lib.mq.MqSecurityHeaderExtractor;
import vdt.mini.shared_lib.mq.MqSecurityHeaders;
import vdt.mini.shared_lib.exception.InboundSecurityException;
import vdt.mini.shared_lib.mq.SecurityRecordInterceptor;
import vdt.mini.shared_lib.security.InboundSecurityDecisionService;
import vdt.mini.shared_lib.security.NonceReplayStore;
import vdt.mini.shared_lib.security.RedisRateLimiter;
import vdt.mini.shared_lib.security.SecurityAuditLogger;
import vdt.mini.shared_lib.security.SecurityDecision;
import vdt.mini.shared_lib.security.SecurityRequestContext;
import vdt.mini.shared_lib.security.SecurityRequestContextHolder;
import vdt.mini.shared_lib.security.SecurityStatusMapper;
import vdt.mini.shared_lib.service.EndpointRegistry;
import vdt.mini.shared_lib.service.IdentityManager;
import vdt.mini.shared_lib.service.RedisSecurityRuntimeKeys;
import vdt.mini.shared_lib.service.SecuritySettingsStore;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InboundMqSecurityDecisionTest {

    private static final String SERVICE_ID = "user-service";
    private static final String ENDPOINT_ID = "endpoint-1";
    private static final String TOPIC = "user.profile.create";
    private static final String CLIENT_ID = "client-1";
    private static final String CLIENT_KEY = "test-client-key";
    private static final String API_KEY = "test-api-key";

    private EndpointRegistry endpointRegistry;
    private SecuritySettingsStore settingsStore;
    private NonceReplayStore nonceReplayStore;
    private InboundSecurityDecisionService decisionService;
    private SecurityRecordInterceptor interceptor;
    private SecurityAuditLogger auditLogger;
    private ObjectMapper objectMapper;

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private RedisRateLimiter rateLimiter;
    @Mock
    private Consumer<String, Object> consumer;
    @Mock
    private IdentityManager identityManager;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();

        endpointRegistry = new EndpointRegistry();
        endpointRegistry.replaceAll(List.of(
                new InboundEndpointDTO(ENDPOINT_ID, "mock-listener", null, TOPIC,
                        "SUB", "MQ", "", true)
        ), List.of());

        settingsStore = new SecuritySettingsStore(redisTemplate, objectMapper);
        nonceReplayStore = new NonceReplayStore(redisTemplate);
        decisionService = new InboundSecurityDecisionService(settingsStore, nonceReplayStore, true);

        SecurityStatusMapper statusMapper = new SecurityStatusMapper();
        auditLogger = new SecurityAuditLogger(objectMapper, statusMapper);

        MqSecurityHeaderExtractor headerExtractor = new MqSecurityHeaderExtractor();

        lenient().when(identityManager.getOrCreateServiceId()).thenReturn(SERVICE_ID);

        interceptor = new SecurityRecordInterceptor(
                endpointRegistry, decisionService, headerExtractor,
                rateLimiter, auditLogger, identityManager, "user-service");

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        mockRateLimitAllowed();
    }

    @AfterEach
    void tearDown() {
        SecurityRequestContextHolder.clear();
    }

    // ───── Helper methods ─────

    private void mockRateLimitAllowed() {
        lenient().when(rateLimiter.checkMqInbound(anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(new RedisRateLimiter.RateLimitResult(true, Long.MAX_VALUE, "key"));
    }

    private void mockRateLimitDenied() {
        when(rateLimiter.checkMqInbound(anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(new RedisRateLimiter.RateLimitResult(false, 0L, "key"));
    }

    private void seedSettings(InboundSettingsDTO settings) throws Exception {
        when(valueOperations.get(eq(RedisSecurityRuntimeKeys.inboundSettings(ENDPOINT_ID))))
                .thenReturn(objectMapper.writeValueAsString(settings));
        settingsStore.pollFromRedis(List.of(ENDPOINT_ID), List.of());
    }

    private void seedRuntime(String clientKey, String authType, String secretKey, String credentialHash) {
        seedRuntime(clientKey, authType, secretKey, credentialHash, true, true);
    }

    private void seedRuntime(String clientKey, String authType, String secretKey,
                             String credentialHash, boolean clientEnabled, boolean clientActive) {
        ClientRuntimeDTO client = new ClientRuntimeDTO(CLIENT_ID, clientKey, "Test Client", "ACTIVE",
                clientEnabled, clientActive, null, null, 1L);
        AuthConfigRuntimeDTO auth = new AuthConfigRuntimeDTO("auth-1", SERVICE_ID, CLIENT_ID, clientKey,
                authType, null, credentialHash, "HmacSHA256", null, secretKey, null, true, "ACTIVE", 1L);
        PermissionRuntimeDTO permission = new PermissionRuntimeDTO("permission-1", SERVICE_ID, ENDPOINT_ID,
                CLIENT_ID, clientKey, true, "ACTIVE", 1L);
        settingsStore.onRuntimeChange(new SecurityRuntimeChangeMessage("event-1", "CLIENT_CHANGED",
                SERVICE_ID, ENDPOINT_ID, CLIENT_ID, "auth-1", "permission-1",
                List.of(), 1L, "2026-06-10T00:00:00",
                new RuntimeChangePayloadDTO(client, List.of(auth), List.of(permission), List.of())));
    }

    private InboundSettingsDTO defaultSettings() {
        InboundSettingsDTO s = new InboundSettingsDTO();
        s.setEndpointId(ENDPOINT_ID);
        s.setName("mock-listener");
        s.setTopic(TOPIC);
        s.setProtocol("MQ");
        s.setEnabled(true);
        s.setEndpointStatus("ACTIVE");
        s.setServiceStatus("ACTIVE");
        s.setAvailable(true);
        s.setLogRetentionDays(30);
        return s;
    }

    private ConsumerRecord<String, Object> record(String payload) {
        return new ConsumerRecord<>(TOPIC, 0, 0L, "key", payload);
    }

    private ConsumerRecord<String, Object> recordWithHeaders(String payload, String clientKey,
                                                               String signature, String timestamp, String nonce) {
        return recordWithHeaders(payload, clientKey, null, signature, timestamp, nonce);
    }

    private ConsumerRecord<String, Object> recordWithHeaders(String payload, String clientKey, String apiKey,
                                                               String signature, String timestamp, String nonce) {
        ConsumerRecord<String, Object> record = record(payload);
        if (clientKey != null) record.headers().add("X-Client-Key", clientKey.getBytes(StandardCharsets.UTF_8));
        if (apiKey != null) record.headers().add("X-Api-Key", apiKey.getBytes(StandardCharsets.UTF_8));
        if (signature != null) record.headers().add("X-Signature", signature.getBytes(StandardCharsets.UTF_8));
        if (timestamp != null) record.headers().add("X-Timestamp", timestamp.getBytes(StandardCharsets.UTF_8));
        if (nonce != null) record.headers().add("X-Nonce", nonce.getBytes(StandardCharsets.UTF_8));
        return record;
    }

    private ConsumerRecord<String, Object> recordWithMqHeaders(String payload, MqSecurityHeaders h) {
        return recordWithHeaders(payload, h.clientKey(), h.apiKey(), h.signature(), h.timestamp(), h.nonce());
    }

    private String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private String hmacBase64(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    private String mqHmacSignature(String topic, String payload, long timestamp, String nonce, String secret) throws Exception {
        String payloadHash = sha256(payload);
        return hmacBase64("MQ\n" + topic + "\n" + timestamp + "\n" + nonce + "\n" + payloadHash, secret);
    }

    // ───── Tests ─────

    @Test
    void shouldAllowWithValidApiKey() throws Exception {
        seedSettings(defaultSettings());
        seedRuntime(CLIENT_KEY, "API_KEY", null, sha256(API_KEY));

        ConsumerRecord<String, Object> result = interceptor.intercept(
                recordWithHeaders("payload", CLIENT_KEY, API_KEY, null, null, null), consumer);

        assertThat(result).isNotNull();
        SecurityRequestContext ctx = SecurityRequestContextHolder.get();
        assertThat(ctx).isNotNull();
        assertThat(ctx.getEndpointId()).isEqualTo(ENDPOINT_ID);
        assertThat(ctx.getClientId()).isEqualTo(CLIENT_ID);
        assertThat(ctx.getClientKey()).isEqualTo(CLIENT_KEY);
        assertThat(ctx.getServiceId()).isEqualTo(SERVICE_ID);
        assertThat(ctx.getProtocol()).isEqualTo("MQ");
        assertThat(ctx.getTopic()).isEqualTo(TOPIC);
    }

    @Test
    void shouldDenyWithoutHeaders() throws Exception {
        seedSettings(defaultSettings());
        seedRuntime(CLIENT_KEY, "API_KEY", null, sha256(API_KEY));

        assertThatThrownBy(() -> interceptor.intercept(record("payload"), consumer))
                .isInstanceOf(InboundSecurityException.class)
                .hasMessageContaining("Missing client key")
                .satisfies(ex -> assertThat(((InboundSecurityException) ex).getErrorCode())
                        .isEqualTo(SecurityErrorCode.AUTH_MISSING));
    }

    @Test
    void shouldDenyWhenApiKeyMissing() throws Exception {
        seedSettings(defaultSettings());
        seedRuntime(CLIENT_KEY, "API_KEY", null, sha256(API_KEY));

        assertThatThrownBy(() -> interceptor.intercept(
                recordWithHeaders("payload", CLIENT_KEY, null, null, null), consumer))
                .isInstanceOf(InboundSecurityException.class)
                .hasMessageContaining("Missing API key")
                .satisfies(ex -> assertThat(((InboundSecurityException) ex).getErrorCode())
                        .isEqualTo(SecurityErrorCode.AUTH_MISSING));
    }

    @Test
    void shouldDenyWithInvalidClientKey() throws Exception {
        seedSettings(defaultSettings());
        seedRuntime("other-key", "API_KEY", null, sha256(API_KEY));

        assertThatThrownBy(() -> interceptor.intercept(
                recordWithHeaders("payload", "unknown-key", null, null, null), consumer))
                .isInstanceOf(InboundSecurityException.class)
                .satisfies(ex -> assertThat(((InboundSecurityException) ex).getErrorCode())
                        .isEqualTo(SecurityErrorCode.CLIENT_KEY_INVALID));
    }

    @Test
    void shouldDenyWhenClientDisabled() throws Exception {
        seedSettings(defaultSettings());
        seedRuntime(CLIENT_KEY, "API_KEY", null, sha256(API_KEY), false, true);

        assertThatThrownBy(() -> interceptor.intercept(
                recordWithHeaders("payload", CLIENT_KEY, null, null, null), consumer))
                .isInstanceOf(InboundSecurityException.class)
                .satisfies(ex -> assertThat(((InboundSecurityException) ex).getErrorCode())
                        .isEqualTo(SecurityErrorCode.CLIENT_KEY_INVALID));
    }

    @Test
    void shouldDenyWhenClientInactive() throws Exception {
        seedSettings(defaultSettings());
        seedRuntime(CLIENT_KEY, "API_KEY", null, sha256(API_KEY), true, false);

        assertThatThrownBy(() -> interceptor.intercept(
                recordWithHeaders("payload", CLIENT_KEY, null, null, null), consumer))
                .isInstanceOf(InboundSecurityException.class)
                .satisfies(ex -> assertThat(((InboundSecurityException) ex).getErrorCode())
                        .isEqualTo(SecurityErrorCode.CLIENT_KEY_INVALID));
    }

    @Test
    void shouldDenyWhenAuthConfigMissing() throws Exception {
        seedSettings(defaultSettings());
        ClientRuntimeDTO client = new ClientRuntimeDTO(CLIENT_ID, CLIENT_KEY, "Test Client", "ACTIVE",
                true, true, null, null, 1L);
        settingsStore.onRuntimeChange(new SecurityRuntimeChangeMessage("event-1", "CLIENT_CHANGED",
                SERVICE_ID, ENDPOINT_ID, CLIENT_ID, null, null,
                List.of(), 1L, "2026-06-10T00:00:00",
                new RuntimeChangePayloadDTO(client, List.of(), List.of(), List.of())));

        assertThatThrownBy(() -> interceptor.intercept(
                recordWithHeaders("payload", CLIENT_KEY, null, null, null), consumer))
                .isInstanceOf(InboundSecurityException.class)
                .satisfies(ex -> assertThat(((InboundSecurityException) ex).getErrorCode())
                        .isEqualTo(SecurityErrorCode.AUTH_CONFIG_INVALID));
    }

    @Test
    void shouldDenyWhenPermissionMissing() throws Exception {
        seedSettings(defaultSettings());
        String credentialHash = sha256(API_KEY);
        ClientRuntimeDTO client = new ClientRuntimeDTO(CLIENT_ID, CLIENT_KEY, "Test Client", "ACTIVE",
                true, true, null, null, 1L);
        AuthConfigRuntimeDTO auth = new AuthConfigRuntimeDTO("auth-1", SERVICE_ID, CLIENT_ID, CLIENT_KEY,
                "API_KEY", null, credentialHash, "HmacSHA256", null, null, null, true, "ACTIVE", 1L);
        settingsStore.onRuntimeChange(new SecurityRuntimeChangeMessage("event-1", "CLIENT_CHANGED",
                SERVICE_ID, ENDPOINT_ID, CLIENT_ID, "auth-1", null,
                List.of(), 1L, "2026-06-10T00:00:00",
                new RuntimeChangePayloadDTO(client, List.of(auth), List.of(), List.of())));

        assertThatThrownBy(() -> interceptor.intercept(
                recordWithHeaders("payload", CLIENT_KEY, null, null, null), consumer))
                .isInstanceOf(InboundSecurityException.class)
                .satisfies(ex -> assertThat(((InboundSecurityException) ex).getErrorCode())
                        .isEqualTo(SecurityErrorCode.PERMISSION_DENIED));
    }

    @Test
    void shouldAllowWithWhitelistBypassNoAuth() throws Exception {
        InboundSettingsDTO settings = defaultSettings();
        settings.setAccessRules(List.of(
                new AccessRuleDTO("WHITELIST", "CLIENT_KEY", CLIENT_KEY, false, null)));
        seedSettings(settings);

        ConsumerRecord<String, Object> result = interceptor.intercept(recordWithHeaders("payload", CLIENT_KEY, null, null, null), consumer);

        assertThat(result).isNotNull();
    }

    @Test
    void shouldDenyBlacklistedTopic() throws Exception {
        InboundSettingsDTO settings = defaultSettings();
        settings.setAccessRules(List.of(
                new AccessRuleDTO("BLACKLIST", "CLIENT_KEY", CLIENT_KEY, false, null)));
        seedSettings(settings);

        assertThatThrownBy(() -> interceptor.intercept(recordWithHeaders("payload", CLIENT_KEY, null, null, null), consumer))
                .isInstanceOf(InboundSecurityException.class)
                .satisfies(ex -> assertThat(((InboundSecurityException) ex).getErrorCode())
                        .isEqualTo(SecurityErrorCode.BLACKLISTED));
    }

    @Test
    void shouldDenyBlacklistedClientKey() throws Exception {
        InboundSettingsDTO settings = defaultSettings();
        settings.setAccessRules(List.of(
                new AccessRuleDTO("BLACKLIST", "CLIENT_KEY", CLIENT_KEY, false, null)));
        seedSettings(settings);

        assertThatThrownBy(() -> interceptor.intercept(
                recordWithHeaders("payload", CLIENT_KEY, null, null, null), consumer))
                .isInstanceOf(InboundSecurityException.class)
                .satisfies(ex -> assertThat(((InboundSecurityException) ex).getErrorCode())
                        .isEqualTo(SecurityErrorCode.BLACKLISTED));
    }

    @Test
    void shouldAllowWithValidHmac() throws Exception {
        seedSettings(defaultSettings());
        String secret = "my-hmac-secret";
        seedRuntime(CLIENT_KEY, "HMAC_SIGNATURE", secret, null);
        long now = Instant.now().toEpochMilli();
        String nonce = "unique-nonce-1";
        String sig = mqHmacSignature(TOPIC, "payload", now, nonce, secret);

        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);

        ConsumerRecord<String, Object> result = interceptor.intercept(
                recordWithHeaders("payload", CLIENT_KEY, sig, String.valueOf(now), nonce),
                consumer);

        assertThat(result).isNotNull();
    }

    @Test
    void shouldDenyInvalidHmacSignature() throws Exception {
        seedSettings(defaultSettings());
        String secret = "my-hmac-secret";
        seedRuntime(CLIENT_KEY, "HMAC_SIGNATURE", secret, null);
        long now = Instant.now().toEpochMilli();

        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);

        assertThatThrownBy(() -> interceptor.intercept(
                recordWithHeaders("payload", CLIENT_KEY, "invalid-signature", String.valueOf(now), "nonce-x"),
                consumer))
                .isInstanceOf(InboundSecurityException.class)
                .satisfies(ex -> assertThat(((InboundSecurityException) ex).getErrorCode())
                        .isEqualTo(SecurityErrorCode.HMAC_INVALID));
    }

    @Test
    void shouldDenyHmacNonceReplay() throws Exception {
        seedSettings(defaultSettings());
        String secret = "my-hmac-secret";
        seedRuntime(CLIENT_KEY, "HMAC_SIGNATURE", secret, null);
        long now = Instant.now().toEpochMilli();
        String nonce = "replayed-nonce";
        String sig = mqHmacSignature(TOPIC, "payload", now, nonce, secret);

        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(false);

        assertThatThrownBy(() -> interceptor.intercept(
                recordWithHeaders("payload", CLIENT_KEY, sig, String.valueOf(now), nonce),
                consumer))
                .isInstanceOf(InboundSecurityException.class)
                .satisfies(ex -> assertThat(((InboundSecurityException) ex).getErrorCode())
                        .isEqualTo(SecurityErrorCode.HMAC_INVALID));
    }

    @Test
    void shouldDenyExpiredHmacTimestamp() throws Exception {
        seedSettings(defaultSettings());
        String secret = "my-hmac-secret";
        seedRuntime(CLIENT_KEY, "HMAC_SIGNATURE", secret, null);
        long oldTimestamp = Instant.now().minusSeconds(3600).toEpochMilli();
        String nonce = "nonce-expired";
        String sig = mqHmacSignature(TOPIC, "payload", oldTimestamp, nonce, secret);

        assertThatThrownBy(() -> interceptor.intercept(
                recordWithHeaders("payload", CLIENT_KEY, sig, String.valueOf(oldTimestamp), nonce),
                consumer))
                .isInstanceOf(InboundSecurityException.class)
                .satisfies(ex -> assertThat(((InboundSecurityException) ex).getErrorCode())
                        .isEqualTo(SecurityErrorCode.HMAC_INVALID));
    }

    @Test
    void shouldDenyWhenRateLimitExceeded() throws Exception {
        InboundSettingsDTO settings = defaultSettings();
        settings.setRateLimit(1);
        settings.setRateLimitWindowSeconds(60);
        seedSettings(settings);
        seedRuntime(CLIENT_KEY, "API_KEY", null, sha256(API_KEY));

        mockRateLimitDenied();

        assertThatThrownBy(() -> interceptor.intercept(
                recordWithHeaders("payload", CLIENT_KEY, API_KEY, null, null, null), consumer))
                .isInstanceOf(InboundSecurityException.class)
                .satisfies(ex -> assertThat(((InboundSecurityException) ex).getErrorCode())
                        .isEqualTo(SecurityErrorCode.RATE_LIMIT_EXCEEDED));
    }

    @Test
    void shouldDenyWhenRequestSizeExceeded() throws Exception {
        InboundSettingsDTO settings = defaultSettings();
        settings.setRequestSizeLimitKb(1);
        seedSettings(settings);
        seedRuntime(CLIENT_KEY, "API_KEY", null, sha256(API_KEY));

        String largePayload = "x".repeat(2049);

        assertThatThrownBy(() -> interceptor.intercept(
                recordWithHeaders(largePayload, CLIENT_KEY, null, null, null), consumer))
                .isInstanceOf(InboundSecurityException.class)
                .satisfies(ex -> assertThat(((InboundSecurityException) ex).getErrorCode())
                        .isEqualTo(SecurityErrorCode.REQUEST_SIZE_EXCEEDED));
    }

    @Test
    void shouldDenyEndpointDisabled() throws Exception {
        InboundSettingsDTO settings = defaultSettings();
        settings.setEnabled(false);
        seedSettings(settings);

        assertThatThrownBy(() -> interceptor.intercept(
                recordWithHeaders("payload", CLIENT_KEY, null, null, null), consumer))
                .isInstanceOf(InboundSecurityException.class)
                .satisfies(ex -> assertThat(((InboundSecurityException) ex).getErrorCode())
                        .isEqualTo(SecurityErrorCode.ENDPOINT_DISABLED));
    }

    @Test
    void shouldDenyEndpointInactive() throws Exception {
        InboundSettingsDTO settings = defaultSettings();
        settings.setEndpointStatus("INACTIVE");
        seedSettings(settings);

        assertThatThrownBy(() -> interceptor.intercept(
                recordWithHeaders("payload", CLIENT_KEY, null, null, null), consumer))
                .isInstanceOf(InboundSecurityException.class)
                .satisfies(ex -> assertThat(((InboundSecurityException) ex).getErrorCode())
                        .isEqualTo(SecurityErrorCode.ENDPOINT_INACTIVE));
    }

    @Test
    void shouldDenyProtocolMismatch() throws Exception {
        InboundSettingsDTO settings = defaultSettings();
        settings.setProtocol("HTTP");
        seedSettings(settings);

        assertThatThrownBy(() -> interceptor.intercept(
                recordWithHeaders("payload", CLIENT_KEY, null, null, null), consumer))
                .isInstanceOf(InboundSecurityException.class)
                .satisfies(ex -> assertThat(((InboundSecurityException) ex).getErrorCode())
                        .isEqualTo(SecurityErrorCode.INVALID_MESSAGE));
    }

    @Test
    void shouldDenyTopicMismatch() throws Exception {
        InboundSettingsDTO settings = defaultSettings();
        settings.setTopic("other.topic");
        seedSettings(settings);

        ConsumerRecord<String, Object> record = new ConsumerRecord<>(TOPIC, 0, 0L, "key", "payload");
        record.headers().add("X-Client-Key", CLIENT_KEY.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> interceptor.intercept(record, consumer))
                .isInstanceOf(InboundSecurityException.class)
                .satisfies(ex -> assertThat(((InboundSecurityException) ex).getErrorCode())
                        .isEqualTo(SecurityErrorCode.INVALID_MESSAGE));
    }

    @Test
    void shouldPassThroughUnregisteredTopic() {
        ConsumerRecord<String, Object> unregistered = new ConsumerRecord<>("other.unregistered.topic", 0, 0L, "key", "payload");

        ConsumerRecord<String, Object> result = interceptor.intercept(unregistered, consumer);

        assertThat(result).isSameAs(unregistered);
        assertThat(SecurityRequestContextHolder.get()).isNull();
    }

    @Test
    void shouldDenyNullRecord() {
        assertThatThrownBy(() -> interceptor.intercept(null, consumer))
                .isInstanceOf(InboundSecurityException.class)
                .hasMessageContaining("null");
    }

    @Test
    void shouldAllowWhenEndpointDisabledViaSettingsButNoSettingsFound() throws Exception {
        when(valueOperations.get(eq(RedisSecurityRuntimeKeys.inboundSettings(ENDPOINT_ID))))
                .thenReturn(null);
        settingsStore.pollFromRedis(List.of(ENDPOINT_ID), List.of());

        ConsumerRecord<String, Object> result = interceptor.intercept(
                recordWithHeaders("payload", CLIENT_KEY, null, null, null), consumer);

        assertThat(result).isNotNull();
    }

    @Test
    void successCallbackShouldLogAllowedOutcome() throws Exception {
        seedSettings(defaultSettings());
        seedRuntime(CLIENT_KEY, "API_KEY", null, sha256(API_KEY));

        interceptor.intercept(recordWithHeaders("payload", CLIENT_KEY, API_KEY, null, null, null), consumer);

        SecurityRequestContext ctx = SecurityRequestContextHolder.get();
        ctx.setDurationMs(10);
        interceptor.success(null, consumer);
        interceptor.afterRecord(null, consumer);

        assertThat(SecurityRequestContextHolder.get()).isNull();
    }

    @Test
    void failureCallbackShouldHandleConsumeError() {
        ConsumerRecord<String, Object> record = record("payload");
        RuntimeException consumeError = new RuntimeException("consumer crashed");

        interceptor.failure(record, consumeError, consumer);
        interceptor.afterRecord(record, consumer);

        SecurityRequestContext ctx = SecurityRequestContextHolder.get();
        assertThat(ctx).isNull();
    }

    @Test
    void shouldDenyWithWrongApiKeyHash() throws Exception {
        seedSettings(defaultSettings());
        seedRuntime(CLIENT_KEY, "API_KEY", null, sha256("different-key"));

        assertThatThrownBy(() -> interceptor.intercept(
                recordWithHeaders("payload", CLIENT_KEY, API_KEY, null, null, null), consumer))
                .isInstanceOf(InboundSecurityException.class)
                .satisfies(ex -> assertThat(((InboundSecurityException) ex).getErrorCode())
                        .isEqualTo(SecurityErrorCode.API_KEY_INVALID));
    }

    @Test
    void shouldRespectGlobalDisabledFlag() throws Exception {
        decisionService = new InboundSecurityDecisionService(settingsStore, nonceReplayStore, false);
        interceptor = new SecurityRecordInterceptor(
                endpointRegistry, decisionService, new MqSecurityHeaderExtractor(),
                rateLimiter, auditLogger, identityManager, "user-service");

        ConsumerRecord<String, Object> result = interceptor.intercept(record("payload"), consumer);

        assertThat(result).isNotNull();
    }
}
