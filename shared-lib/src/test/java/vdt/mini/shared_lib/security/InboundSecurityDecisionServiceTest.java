package vdt.mini.shared_lib.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import vdt.mini.shared_lib.document.AccessPermissionDTO;
import vdt.mini.shared_lib.document.AccessRuleDTO;
import vdt.mini.shared_lib.document.AuthConfigDTO;
import vdt.mini.shared_lib.document.AuthConfigRuntimeDTO;
import vdt.mini.shared_lib.document.ClientRuntimeDTO;
import vdt.mini.shared_lib.document.InboundSettingsDTO;
import vdt.mini.shared_lib.document.PermissionRuntimeDTO;
import vdt.mini.shared_lib.document.RuntimeChangePayloadDTO;
import vdt.mini.shared_lib.document.SecurityRuntimeChangeMessage;
import vdt.mini.shared_lib.enums.SecurityErrorCode;
import vdt.mini.shared_lib.service.EndpointRegistry;
import vdt.mini.shared_lib.service.RedisSecurityRuntimeKeys;
import vdt.mini.shared_lib.service.SecuritySettingsStore;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InboundSecurityDecisionServiceTest {
    private static final String SERVICE_ID = "service-1";
    private static final String ENDPOINT_ID = "endpoint-1";
    private static final String CLIENT_ID = "client-1";
    private static final String CLIENT_KEY = "client-key-1";
    private static final String API_KEY = "ak_test-secret";

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private ObjectMapper objectMapper;
    private SecuritySettingsStore settingsStore;
    private NonceReplayStore nonceReplayStore;
    private InboundSecurityDecisionService decisionService;
    private EndpointRegistry.InboundHttpEndpoint endpoint;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        settingsStore = new SecuritySettingsStore(redisTemplate, objectMapper);
        nonceReplayStore = new NonceReplayStore(redisTemplate);
        decisionService = new InboundSecurityDecisionService(settingsStore, nonceReplayStore, true);
        endpoint = new EndpointRegistry.InboundHttpEndpoint(ENDPOINT_ID, "Create Order", "POST", "/orders", "HTTP", null);
    }

    @Test
    void decide_shouldDenyNotRegistered_whenEndpointMissing() {
        SecurityDecision decision = decisionService.decide(request(), null, context());

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.errorCode()).isEqualTo(SecurityErrorCode.ENDPOINT_NOT_REGISTERED);
    }

    @Test
    void decide_shouldAllow_whenSettingsMissingForMigrationCompatibility() {
        SecurityDecision decision = decisionService.decide(request(), endpoint, context());

        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void decide_shouldDenyDisabledInactiveProtocolAndRequestSizeCases() throws Exception {
        assertDenied(settings(settings -> settings.setEnabled(false)), SecurityErrorCode.ENDPOINT_DISABLED);
        assertDenied(settings(settings -> settings.setEndpointStatus("INACTIVE")), SecurityErrorCode.ENDPOINT_INACTIVE);
        assertDenied(settings(settings -> settings.setProtocol("MQ")), SecurityErrorCode.INVALID_REQUEST);
        InboundSettingsDTO smallLimit = settings(settings -> settings.setRequestSizeLimitKb(1));
        MockHttpServletRequest request = request();
        request.setContent(new byte[2049]);
        loadSettings(smallLimit);
        SecurityRequestContext context = context();
        context.setRequestSizeBytes(2049L);

        SecurityDecision decision = decisionService.decide(request, endpoint, context);

        assertThat(decision.errorCode()).isEqualTo(SecurityErrorCode.REQUEST_SIZE_EXCEEDED);
    }

    @Test
    void decide_shouldDenyMissingOrInvalidApiKey_whenAuthRequired() throws Exception {
        loadSettings(settings(settings -> {
            settings.setAuthConfigs(List.of(apiKeyAuthConfig()));
            settings.setPermissions(List.of(new AccessPermissionDTO("permission-2", "other-client", "other-key", ENDPOINT_ID)));
        }));

        assertThat(decisionService.decide(request(), endpoint, context()).errorCode()).isEqualTo(SecurityErrorCode.AUTH_MISSING);

        MockHttpServletRequest request = request();
        request.addHeader(InboundSecurityDecisionService.CLIENT_KEY_HEADER, "bad-key");
        assertThat(decisionService.decide(request, endpoint, context()).errorCode()).isEqualTo(SecurityErrorCode.API_KEY_INVALID);
    }

    @Test
    void decide_shouldRequireAuthAndPermissionForNormalRequest() throws Exception {
        loadSettings(settings(settings -> {
            settings.setAuthConfigs(List.of());
            settings.setPermissions(List.of());
        }));

        SecurityDecision missingAuth = decisionService.decide(request(), endpoint, context());

        assertThat(missingAuth.allowed()).isFalse();
        assertThat(missingAuth.errorCode()).isEqualTo(SecurityErrorCode.AUTH_MISSING);

        loadRuntime(false, "API_KEY", null);
        SecurityDecision missingPermission = decisionService.decide(authenticatedRequest(), endpoint, context());

        assertThat(missingPermission.allowed()).isFalse();
        assertThat(missingPermission.errorCode()).isEqualTo(SecurityErrorCode.WHITELIST_NOT_MATCHED);
    }

    @Test
    void decide_shouldDenyPermissionMissingAndAllowWhenRuntimePermissionExists() throws Exception {
        loadSettings(settings(settings -> {
            settings.setAuthConfigs(List.of(apiKeyAuthConfig()));
            settings.setPermissions(List.of(new AccessPermissionDTO("permission-2", "other-client", "other-key", ENDPOINT_ID)));
        }));
        loadRuntime(false, "API_KEY", null);
        MockHttpServletRequest request = authenticatedRequest();

        SecurityDecision denied = decisionService.decide(request, endpoint, context());

        assertThat(denied.errorCode()).isEqualTo(SecurityErrorCode.WHITELIST_NOT_MATCHED);

        loadRuntime(true, "API_KEY", null);
        SecurityDecision allowed = decisionService.decide(apiKeyRequest(), endpoint, context());

        assertThat(allowed.allowed()).isTrue();
        assertThat(allowed.clientId()).isEqualTo(CLIENT_ID);
    }

    @Test
    void decide_shouldValidateApiKeyHash() throws Exception {
        loadSettings(settings(settings -> settings.setAuthConfigs(List.of(apiKeyAuthConfig()))));
        loadRuntime(true, "API_KEY", null);

        SecurityDecision missingApiKey = decisionService.decide(authenticatedRequest(), endpoint, context());
        assertThat(missingApiKey.errorCode()).isEqualTo(SecurityErrorCode.AUTH_MISSING);

        MockHttpServletRequest wrongApiKey = authenticatedRequest();
        wrongApiKey.addHeader(InboundSecurityDecisionService.API_KEY_HEADER, "wrong-api-key");
        SecurityRequestContext invalidContext = context();
        SecurityDecision invalidApiKey = decisionService.decide(wrongApiKey, endpoint, invalidContext);
        assertThat(invalidApiKey.errorCode()).isEqualTo(SecurityErrorCode.API_KEY_INVALID);
        assertThat(invalidContext.getAuthType()).isEqualTo("API_KEY");
        assertThat(invalidContext.getDenyReason()).isEqualTo("Invalid API key");

        SecurityRequestContext allowedContext = context();
        SecurityDecision allowed = decisionService.decide(apiKeyRequest(), endpoint, allowedContext);
        assertThat(allowed.allowed()).isTrue();
        assertThat(allowedContext.getAuthType()).isEqualTo("API_KEY");
        assertThat(allowedContext.getDenyReason()).isNull();
    }

    @Test
    void decide_shouldDenyBlacklistedAndBypassAuthWhenWhitelisted() throws Exception {
        assertDenied(settings(settings -> settings.setAccessRules(List.of(new AccessRuleDTO("BLACKLIST", "IP", "127.0.0.1", false, null)))),
                SecurityErrorCode.BLACKLISTED);

        loadSettings(settings(settings -> settings.setAccessRules(List.of(new AccessRuleDTO("WHITELIST", "IP", "127.0.0.1", false, null)))));
        SecurityDecision whitelisted = decisionService.decide(request(), endpoint, context());

        assertThat(whitelisted.allowed()).isTrue();
    }

    @Test
    void decide_shouldContinueNormalAuthFlowWhenWhitelistDoesNotMatch() throws Exception {
        loadSettings(settings(settings -> settings.setAccessRules(List.of(new AccessRuleDTO("WHITELIST", "IP", "10.0.0.1", false, null)))));

        SecurityDecision decision = decisionService.decide(request(), endpoint, context());

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.errorCode()).isEqualTo(SecurityErrorCode.AUTH_MISSING);
    }

    @Test
    void decide_shouldValidateHmacAndRejectReplay() throws Exception {
        loadSettings(settings(settings -> settings.setAuthConfigs(List.of(hmacAuthConfig()))));
        loadRuntime(true, "HMAC_SIGNATURE", "secret");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("security:runtime:nonce:http:in:service-1:endpoint-1:client-key-1:nonce-1"), eq("1"), any()))
                .thenReturn(true, false);
        MockHttpServletRequest request = authenticatedRequest();
        long timestamp = Instant.now().toEpochMilli();
        request.addHeader(InboundSecurityDecisionService.TIMESTAMP_HEADER, String.valueOf(timestamp));
        request.addHeader(InboundSecurityDecisionService.NONCE_HEADER, "nonce-1");
        request.addHeader(InboundSecurityDecisionService.SIGNATURE_HEADER, signature("POST\n/orders\n" + timestamp + "\nnonce-1", "secret"));

        assertThat(decisionService.decide(request, endpoint, context()).allowed()).isTrue();
        assertThat(decisionService.decide(request, endpoint, context()).errorCode()).isEqualTo(SecurityErrorCode.HMAC_INVALID);
    }

    private void assertDenied(InboundSettingsDTO settings, SecurityErrorCode expected) throws Exception {
        loadSettings(settings);
        SecurityDecision decision = decisionService.decide(request(), endpoint, context());

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.errorCode()).isEqualTo(expected);
    }

    private void loadSettings(InboundSettingsDTO settings) throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(RedisSecurityRuntimeKeys.inboundSettings(ENDPOINT_ID))).thenReturn(objectMapper.writeValueAsString(settings));
        settingsStore.pollFromRedis(List.of(ENDPOINT_ID), List.of());
    }

    private void loadRuntime(boolean includePermission, String authType, String secretKey) {
        AuthConfigRuntimeDTO auth = new AuthConfigRuntimeDTO("auth-1", SERVICE_ID, CLIENT_ID, CLIENT_KEY, authType, null,
                sha256(API_KEY), "HmacSHA256", null, secretKey, null, true, "ACTIVE", 1L);
        PermissionRuntimeDTO permission = new PermissionRuntimeDTO("permission-1", SERVICE_ID, ENDPOINT_ID, CLIENT_ID, CLIENT_KEY, true, "ACTIVE", 1L);
        settingsStore.onRuntimeChange(new SecurityRuntimeChangeMessage("event-1", "CLIENT_CHANGED", SERVICE_ID, ENDPOINT_ID, CLIENT_ID,
                "auth-1", "permission-1", List.of(), System.nanoTime(), "2026-06-09T00:00:00",
                new RuntimeChangePayloadDTO(client(), List.of(auth), includePermission ? List.of(permission) : List.of(), List.of())));
    }

    private ClientRuntimeDTO client() {
        return new ClientRuntimeDTO(CLIENT_ID, CLIENT_KEY, "Client", "ACTIVE", true, true, null, null, 1L);
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/orders");
        request.setRemoteAddr("127.0.0.1");
        return request;
    }

    private MockHttpServletRequest authenticatedRequest() {
        MockHttpServletRequest request = request();
        request.addHeader(InboundSecurityDecisionService.CLIENT_KEY_HEADER, CLIENT_KEY);
        return request;
    }

    private MockHttpServletRequest apiKeyRequest() {
        MockHttpServletRequest request = authenticatedRequest();
        request.addHeader(InboundSecurityDecisionService.API_KEY_HEADER, API_KEY);
        return request;
    }

    private SecurityRequestContext context() {
        SecurityRequestContext context = new SecurityRequestContext();
        context.setServiceId(SERVICE_ID);
        context.setMethod("POST");
        context.setPath("/orders");
        context.setSourceIp("127.0.0.1");
        return context;
    }

    private InboundSettingsDTO settings(SettingsCustomizer customizer) {
        InboundSettingsDTO settings = new InboundSettingsDTO(ENDPOINT_ID, "Create Order", "/orders", null, "POST", "HTTP", true,
                "ACTIVE", "ACTIVE", true, null, null, null, null, null, null, 30, null, null, null,
                List.of(), List.of(), List.of());
        customizer.customize(settings);
        return settings;
    }

    private String signature(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    private AuthConfigDTO apiKeyAuthConfig() {
        return new AuthConfigDTO("API_KEY", "api-ref", sha256(API_KEY), null, null, null, CLIENT_KEY, true);
    }

    private AuthConfigDTO hmacAuthConfig() {
        return new AuthConfigDTO("HMAC_SIGNATURE", "hmac-ref", sha256(API_KEY), null, "HmacSHA256", null, CLIENT_KEY, true);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        }
    }

    @FunctionalInterface
    private interface SettingsCustomizer {
        void customize(InboundSettingsDTO settings);
    }
}
