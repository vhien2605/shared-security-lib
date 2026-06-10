package vdt.mini.shared_lib.security;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vdt.mini.shared_lib.document.AccessPermissionDTO;
import vdt.mini.shared_lib.document.AccessRuleDTO;
import vdt.mini.shared_lib.document.AuthConfigRuntimeDTO;
import vdt.mini.shared_lib.document.ClientRuntimeDTO;
import vdt.mini.shared_lib.document.InboundSettingsDTO;
import vdt.mini.shared_lib.document.PermissionRuntimeDTO;
import vdt.mini.shared_lib.enums.SecurityErrorCode;
import vdt.mini.shared_lib.enums.SecurityResultStatus;
import vdt.mini.shared_lib.mq.MqSecurityHeaders;
import vdt.mini.shared_lib.mq.MqSecurityRequest;
import vdt.mini.shared_lib.service.EndpointRegistry;
import vdt.mini.shared_lib.service.SecuritySettingsStore;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class InboundSecurityDecisionService {
    private static final Logger log = LoggerFactory.getLogger(InboundSecurityDecisionService.class);
    public static final String CLIENT_KEY_HEADER = "X-Client-Key";
    public static final String API_KEY_HEADER = "X-Api-Key";
    public static final String SIGNATURE_HEADER = "X-Signature";
    public static final String TIMESTAMP_HEADER = "X-Timestamp";
    public static final String NONCE_HEADER = "X-Nonce";
    private static final Duration SIGNATURE_VALIDITY = Duration.ofMinutes(5);

    private final SecuritySettingsStore settingsStore;
    private final NonceReplayStore nonceReplayStore;
    private final boolean globalEnabled;

    public InboundSecurityDecisionService(SecuritySettingsStore settingsStore,
                                          NonceReplayStore nonceReplayStore,
                                          @Value("${app.security.inbound.enabled:true}") boolean globalEnabled) {
        this.settingsStore = settingsStore;
        this.nonceReplayStore = nonceReplayStore;
        this.globalEnabled = globalEnabled;
    }

    public SecurityDecision decide(HttpServletRequest request, EndpointRegistry.InboundHttpEndpoint endpoint,
                                   SecurityRequestContext context) {
        if (!globalEnabled) {
            return SecurityDecision.allow(null, null, null);
        }
        if (endpoint == null) {
            return deny(SecurityErrorCode.ENDPOINT_NOT_REGISTERED, "Endpoint is not registered", context, null, null, null);
        }
        context.setEndpointId(endpoint.endpointId());
        context.setEndpointName(endpoint.name());
        context.setProtocol(endpoint.protocol());

        InboundSettingsDTO settings = settingsStore.getInboundSettings(endpoint.endpointId());
        if (settings == null) {
            log.info("Inbound settings missing endpointId={}; allowing request for migration compatibility", endpoint.endpointId());
            return SecurityDecision.allow(endpoint.endpointId(), null, null);
        }
        context.setInboundSettings(settings);
        copySettingsToContext(settings, context);

        if (Boolean.FALSE.equals(settings.getEnabled())) {
            return deny(SecurityErrorCode.ENDPOINT_DISABLED, "Endpoint is disabled", context, endpoint.endpointId(), null, null);
        }
        if (isInactive(settings.getEndpointStatus()) || isInactive(settings.getServiceStatus()) || Boolean.FALSE.equals(settings.getAvailable())) {
            return deny(SecurityErrorCode.ENDPOINT_INACTIVE, "Endpoint is inactive or unavailable", context, endpoint.endpointId(), null, null);
        }
        if (!isHttpProtocol(settings.getProtocol())) {
            return deny(SecurityErrorCode.INVALID_REQUEST, "Protocol mismatch", context, endpoint.endpointId(), null, null);
        }
        if (hasText(settings.getMethod()) && !settings.getMethod().equalsIgnoreCase(request.getMethod())) {
            return deny(SecurityErrorCode.INVALID_REQUEST, "HTTP method mismatch", context, endpoint.endpointId(), null, null);
        }
        if (isRequestTooLarge(context.getRequestSizeBytes(), settings.getRequestSizeLimitKb())) {
            return deny(SecurityErrorCode.REQUEST_SIZE_EXCEEDED, "Request size exceeded", context, endpoint.endpointId(), null, null);
        }
        AccessRuleDecision accessRuleDecision = evaluateAccessRules(settings.getAccessRules(), request, context);
        if (!accessRuleDecision.allowed()) {
            return deny(accessRuleDecision.errorCode(), accessRuleDecision.message(), context, endpoint.endpointId(), null, null);
        }
        if (accessRuleDecision.whitelisted()) {
            return SecurityDecision.allow(endpoint.endpointId(), null, null);
        }

        String clientKey = request.getHeader(CLIENT_KEY_HEADER);
        if (!hasText(clientKey)) {
            return deny(SecurityErrorCode.AUTH_MISSING, "Missing client key", context, endpoint.endpointId(), null, null);
        }
        Optional<String> clientId = settingsStore.resolveClientId(context.getServiceId(), clientKey);
        if (clientId.isEmpty()) {
            return deny(SecurityErrorCode.API_KEY_INVALID, "Invalid API key", context, endpoint.endpointId(), null, clientKey);
        }
        Optional<ClientRuntimeDTO> client = settingsStore.getClient(context.getServiceId(), clientId.get());
        if (client.isEmpty() || !Boolean.TRUE.equals(client.get().getEnabled()) || !Boolean.TRUE.equals(client.get().getActive())) {
            return deny(SecurityErrorCode.API_KEY_INVALID, "Client is disabled or inactive", context, endpoint.endpointId(), clientId.get(), clientKey);
        }
        Optional<AuthConfigRuntimeDTO> auth = settingsStore.getAuthConfig(context.getServiceId(), endpoint.endpointId(), clientId.get());
        if (auth.isEmpty() || !Boolean.TRUE.equals(auth.get().getEnabled())) {
            return deny(SecurityErrorCode.API_KEY_INVALID, "Auth config missing or disabled", context, endpoint.endpointId(), clientId.get(), clientKey);
        }
        context.setAuthType(auth.get().getType());
        if (!hasPermission(settings, context.getServiceId(), endpoint.endpointId(), clientId.get(), clientKey)) {
            return deny(SecurityErrorCode.WHITELIST_NOT_MATCHED, "Permission missing or disabled", context, endpoint.endpointId(), clientId.get(), clientKey);
        }
        if (isApiKey(auth.get())) {
            String apiKey = request.getHeader(API_KEY_HEADER);
            if (!hasText(apiKey)) {
                return deny(SecurityErrorCode.AUTH_MISSING, "Missing API key", context, endpoint.endpointId(), clientId.get(), clientKey);
            }
            if (!validApiKey(apiKey, auth.get())) {
                return deny(SecurityErrorCode.API_KEY_INVALID, "Invalid API key", context, endpoint.endpointId(), clientId.get(), clientKey);
            }
        }
        if (isHmac(auth.get()) && !validHmac(request, context, auth.get(), clientKey)) {
            return deny(SecurityErrorCode.HMAC_INVALID, "Invalid HMAC signature", context, endpoint.endpointId(), clientId.get(), clientKey);
        }
        context.setClientId(clientId.get());
        context.setClientKey(clientKey);
        return SecurityDecision.allow(endpoint.endpointId(), clientId.get(), clientKey);
    }

    public SecurityDecision decide(MqSecurityRequest request, EndpointRegistry.InboundMqEndpoint endpoint,
                                   SecurityRequestContext context) {
        if (!globalEnabled) {
            return SecurityDecision.allow(null, null, null);
        }
        if (endpoint == null) {
            return deny(SecurityErrorCode.LISTENER_NOT_REGISTERED, "Kafka listener is not registered", context, null, null, null);
        }
        context.setEndpointId(endpoint.endpointId());
        context.setEndpointName(endpoint.name());
        context.setProtocol(endpoint.protocol());
        context.setTopic(endpoint.topic());

        InboundSettingsDTO settings = settingsStore.getInboundSettings(endpoint.endpointId());
        if (settings == null) {
            log.info("Inbound MQ settings missing endpointId={}; allowing message for migration compatibility", endpoint.endpointId());
            return SecurityDecision.allow(endpoint.endpointId(), null, null);
        }
        context.setInboundSettings(settings);
        copySettingsToContext(settings, context);
        context.setMethod(settings.getMethod());
        context.setTopic(hasText(settings.getTopic()) ? settings.getTopic() : endpoint.topic());

        if (Boolean.FALSE.equals(settings.getEnabled())) {
            return deny(SecurityErrorCode.ENDPOINT_DISABLED, "Endpoint is disabled", context, endpoint.endpointId(), null, null);
        }
        if (isInactive(settings.getEndpointStatus()) || isInactive(settings.getServiceStatus()) || Boolean.FALSE.equals(settings.getAvailable())) {
            return deny(SecurityErrorCode.ENDPOINT_INACTIVE, "Endpoint is inactive or unavailable", context, endpoint.endpointId(), null, null);
        }
        if (!isMqProtocol(settings.getProtocol())) {
            return deny(SecurityErrorCode.INVALID_MESSAGE, "Protocol mismatch", context, endpoint.endpointId(), null, null);
        }
        if (hasText(settings.getTopic()) && !settings.getTopic().equals(request.topic())) {
            return deny(SecurityErrorCode.INVALID_MESSAGE, "Kafka topic mismatch", context, endpoint.endpointId(), null, null);
        }
        if (isRequestTooLarge(request.messageSizeBytes(), settings.getRequestSizeLimitKb())) {
            return deny(SecurityErrorCode.REQUEST_SIZE_EXCEEDED, "Message size exceeded", context, endpoint.endpointId(), null, null);
        }
        MqSecurityHeaders headers = request.headers();
        AccessRuleDecision accessRuleDecision = evaluateMqAccessRules(settings.getAccessRules(), headers, request.topic(), context);
        if (!accessRuleDecision.allowed()) {
            return deny(accessRuleDecision.errorCode(), accessRuleDecision.message(), context, endpoint.endpointId(), null, headers == null ? null : headers.clientKey());
        }
        if (accessRuleDecision.whitelisted()) {
            return SecurityDecision.allow(endpoint.endpointId(), null, headers == null ? null : headers.clientKey());
        }
        if (headers == null || !hasText(headers.clientKey())) {
            return deny(SecurityErrorCode.AUTH_MISSING, "Missing client key", context, endpoint.endpointId(), null, null);
        }
        String clientKey = headers.clientKey();
        Optional<String> clientId = settingsStore.resolveClientId(context.getServiceId(), clientKey);
        if (clientId.isEmpty()) {
            return deny(SecurityErrorCode.API_KEY_INVALID, "Invalid API key", context, endpoint.endpointId(), null, clientKey);
        }
        Optional<ClientRuntimeDTO> client = settingsStore.getClient(context.getServiceId(), clientId.get());
        if (client.isEmpty() || !Boolean.TRUE.equals(client.get().getEnabled()) || !Boolean.TRUE.equals(client.get().getActive())) {
            return deny(SecurityErrorCode.API_KEY_INVALID, "Client is disabled or inactive", context, endpoint.endpointId(), clientId.get(), clientKey);
        }
        Optional<AuthConfigRuntimeDTO> auth = settingsStore.getAuthConfig(context.getServiceId(), endpoint.endpointId(), clientId.get());
        if (auth.isEmpty() || !Boolean.TRUE.equals(auth.get().getEnabled())) {
            return deny(SecurityErrorCode.API_KEY_INVALID, "Auth config missing or disabled", context, endpoint.endpointId(), clientId.get(), clientKey);
        }
        context.setAuthType(auth.get().getType());
        if (!hasPermission(settings, context.getServiceId(), endpoint.endpointId(), clientId.get(), clientKey)) {
            return deny(SecurityErrorCode.WHITELIST_NOT_MATCHED, "Permission missing or disabled", context, endpoint.endpointId(), clientId.get(), clientKey);
        }
        if (isApiKey(auth.get())) {
            String apiKey = headers.apiKey();
            if (!hasText(apiKey)) {
                return deny(SecurityErrorCode.AUTH_MISSING, "Missing API key", context, endpoint.endpointId(), clientId.get(), clientKey);
            }
            if (!validApiKey(apiKey, auth.get())) {
                return deny(SecurityErrorCode.API_KEY_INVALID, "Invalid API key", context, endpoint.endpointId(), clientId.get(), clientKey);
            }
        }
        if (isHmac(auth.get()) && !validMqHmac(request, context, auth.get(), clientKey)) {
            return deny(SecurityErrorCode.HMAC_INVALID, "Invalid HMAC signature", context, endpoint.endpointId(), clientId.get(), clientKey);
        }
        context.setClientId(clientId.get());
        context.setClientKey(clientKey);
        return SecurityDecision.allow(endpoint.endpointId(), clientId.get(), clientKey);
    }

    private SecurityDecision deny(SecurityErrorCode errorCode, String message, SecurityRequestContext context,
                                  String endpointId, String clientId, String clientKey) {
        context.setClientId(clientId);
        context.setClientKey(clientKey);
        context.setDenyReason(message);
        return SecurityDecision.deny(SecurityResultStatus.DENIED, errorCode, message, endpointId, clientId, clientKey);
    }

    private void copySettingsToContext(InboundSettingsDTO settings, SecurityRequestContext context) {
        context.setEndpointName(settings.getName());
        context.setProtocol(settings.getProtocol());
        context.setRateLimit(settings.getRateLimit());
        context.setRateLimitWindowSeconds(settings.getRateLimitWindowSeconds());
        context.setThresholdMs(settings.getResponseTimeThresholdMs());
        context.setTimeoutMs(settings.getTimeoutMs());
        context.setRetentionDays(settings.getLogRetentionDays());
    }

    private boolean isInactive(String status) {
        if (!hasText(status)) {
            return false;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        return normalized.equals("INACTIVE") || normalized.equals("DISABLED") || normalized.equals("UNAVAILABLE");
    }

    private boolean isHttpProtocol(String protocol) {
        return !hasText(protocol) || "HTTP".equalsIgnoreCase(protocol) || "WEBHOOK".equalsIgnoreCase(protocol);
    }

    private boolean isMqProtocol(String protocol) {
        return "MQ".equalsIgnoreCase(protocol);
    }

    private boolean isRequestTooLarge(long contentLength, Integer limitKb) {
        return contentLength >= 0 && limitKb != null && limitKb > 0 && contentLength > limitKb * 1024L;
    }

    private AccessRuleDecision evaluateAccessRules(List<AccessRuleDTO> rules, HttpServletRequest request, SecurityRequestContext context) {
        boolean hasWhitelist = false;
        boolean whitelistMatched = false;
        for (AccessRuleDTO rule : safeList(rules)) {
            if (rule == null || isExpired(rule.getExpiresAt())) {
                continue;
            }
            String type = normalize(rule.getType());
            boolean matched = matchesRule(rule, request, context);
            if ("BLACKLIST".equals(type) && matched) {
                return AccessRuleDecision.denied(SecurityErrorCode.BLACKLISTED, "Request matched blacklist rule");
            }
            if ("WHITELIST".equals(type)) {
                hasWhitelist = true;
                whitelistMatched = whitelistMatched || matched;
            }
        }
        return AccessRuleDecision.allowed(whitelistMatched);
    }

    private boolean matchesRule(AccessRuleDTO rule, HttpServletRequest request, SecurityRequestContext context) {
        String valueType = normalize(rule.getValueType());
        String value = rule.getValue();
        if (!hasText(value)) {
            return false;
        }
        if ("CLIENT_ID".equals(valueType)) {
            return value.equals(context.getClientId());
        }
        if ("CLIENT_KEY".equals(valueType)) {
            return value.equals(request.getHeader(CLIENT_KEY_HEADER));
        }
        return value.equals(context.getSourceIp()) || value.equals(request.getRemoteAddr());
    }

    private AccessRuleDecision evaluateMqAccessRules(List<AccessRuleDTO> rules, MqSecurityHeaders headers,
                                                     String topic, SecurityRequestContext context) {
        boolean hasWhitelist = false;
        boolean whitelistMatched = false;
        for (AccessRuleDTO rule : safeList(rules)) {
            if (rule == null || isExpired(rule.getExpiresAt())) {
                continue;
            }
            String type = normalize(rule.getType());
            boolean matched = matchesMqRule(rule, headers, topic, context);
            if ("BLACKLIST".equals(type) && matched) {
                return AccessRuleDecision.denied(SecurityErrorCode.BLACKLISTED, "Message matched blacklist rule");
            }
            if ("WHITELIST".equals(type)) {
                hasWhitelist = true;
                whitelistMatched = whitelistMatched || matched;
            }
        }
        return AccessRuleDecision.allowed(!hasWhitelist || whitelistMatched ? whitelistMatched : false);
    }

    private boolean matchesMqRule(AccessRuleDTO rule, MqSecurityHeaders headers, String topic, SecurityRequestContext context) {
        String valueType = normalize(rule.getValueType());
        String value = rule.getValue();
        if (!hasText(value)) {
            return false;
        }
        if ("CLIENT_ID".equals(valueType)) {
            return value.equals(context.getClientId());
        }
        if ("CLIENT_KEY".equals(valueType)) {
            return headers != null && value.equals(headers.clientKey());
        }
        if ("TOPIC".equals(valueType)) {
            return value.equals(topic);
        }
        return value.equals(topic);
    }

    private boolean hasPermission(InboundSettingsDTO settings, String serviceId, String endpointId, String clientId, String clientKey) {
        Optional<PermissionRuntimeDTO> runtimePermission = settingsStore.getPermission(serviceId, endpointId, clientId);
        if (runtimePermission.isPresent()) {
            return Boolean.TRUE.equals(runtimePermission.get().getEnabled());
        }
        for (AccessPermissionDTO permission : safeList(settings.getPermissions())) {
            if (permission == null) {
                continue;
            }
            boolean clientMatches = clientId.equals(permission.getClientId()) || clientKey.equals(permission.getClientKey());
            boolean endpointMatches = !hasText(permission.getInboundEndpointId()) || endpointId.equals(permission.getInboundEndpointId());
            if (clientMatches && endpointMatches) {
                return true;
            }
        }
        return false;
    }

    private boolean isHmac(AuthConfigRuntimeDTO runtimeAuth) {
        return isHmacType(runtimeAuth.getType());
    }

    private boolean isApiKey(AuthConfigRuntimeDTO runtimeAuth) {
        return "API_KEY".equalsIgnoreCase(runtimeAuth.getType());
    }

    private boolean validApiKey(String apiKey, AuthConfigRuntimeDTO auth) {
        if (!hasText(apiKey) || !hasText(auth.getCredentialHash())) {
            return false;
        }
        return auth.getCredentialHash().equals(sha256(apiKey));
    }

    private boolean isHmacType(String type) {
        return "HMAC".equalsIgnoreCase(type) || "HMAC_SIGNATURE".equalsIgnoreCase(type);
    }

    private boolean validHmac(HttpServletRequest request, SecurityRequestContext context, AuthConfigRuntimeDTO auth, String clientKey) {
        String timestamp = request.getHeader(TIMESTAMP_HEADER);
        String nonce = request.getHeader(NONCE_HEADER);
        String signature = request.getHeader(SIGNATURE_HEADER);
        if (!hasText(timestamp) || !hasText(nonce) || !hasText(signature) || !hasText(auth.getSecretKey())) {
            return false;
        }
        Instant timestampInstant;
        try {
            timestampInstant = Instant.ofEpochMilli(Long.parseLong(timestamp));
        } catch (NumberFormatException ex) {
            return false;
        }
        if (timestampInstant.plus(SIGNATURE_VALIDITY).isBefore(Instant.now()) || timestampInstant.minus(SIGNATURE_VALIDITY).isAfter(Instant.now())) {
            return false;
        }
        if (nonceReplayStore.seenOrStore(context.getServiceId(), context.getEndpointId(), clientKey, nonce, SIGNATURE_VALIDITY)) {
            return false;
        }
        String payload = request.getMethod() + "\n" + request.getRequestURI() + "\n" + timestamp + "\n" + nonce;
        return signature.equals(hmac(payload, auth.getSecretKey(), auth.getAlgorithm()));
    }

    private boolean validMqHmac(MqSecurityRequest request, SecurityRequestContext context, AuthConfigRuntimeDTO auth, String clientKey) {
        MqSecurityHeaders headers = request.headers();
        if (headers == null || !hasText(headers.timestamp()) || !hasText(headers.nonce())
                || !hasText(headers.signature()) || !hasText(auth.getSecretKey())) {
            return false;
        }
        Instant timestampInstant;
        try {
            timestampInstant = Instant.ofEpochMilli(Long.parseLong(headers.timestamp()));
        } catch (NumberFormatException ex) {
            return false;
        }
        if (timestampInstant.plus(SIGNATURE_VALIDITY).isBefore(Instant.now())
                || timestampInstant.minus(SIGNATURE_VALIDITY).isAfter(Instant.now())) {
            return false;
        }
        if (nonceReplayStore.seenOrStoreMq(context.getServiceId(), context.getEndpointId(), clientKey, headers.nonce(), SIGNATURE_VALIDITY)) {
            return false;
        }
        String payloadHash = sha256(String.valueOf(request.value()));
        String payload = "MQ\n" + request.topic() + "\n" + headers.timestamp() + "\n" + headers.nonce() + "\n" + payloadHash;
        return headers.signature().equals(hmac(payload, auth.getSecretKey(), auth.getAlgorithm()));
    }

    private String hmac(String payload, String secret, String algorithm) {
        try {
            String macAlgorithm = hasText(algorithm) ? algorithm : "HmacSHA256";
            Mac mac = Mac.getInstance(macAlgorithm);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), macAlgorithm));
            return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            log.warn("HMAC calculation failed", ex);
            return "";
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        }
    }

    private boolean isExpired(String expiresAt) {
        if (!hasText(expiresAt)) {
            return false;
        }
        try {
            return Instant.parse(expiresAt).isBefore(Instant.now());
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static <T> List<T> safeList(List<T> value) {
        return value == null ? List.of() : value;
    }

    private record AccessRuleDecision(boolean allowed, boolean whitelisted, SecurityErrorCode errorCode,
                                      String message) {
        static AccessRuleDecision allowed(boolean whitelisted) {
            return new AccessRuleDecision(true, whitelisted, null, "OK");
        }

        static AccessRuleDecision denied(SecurityErrorCode errorCode, String message) {
            return new AccessRuleDecision(false, false, errorCode, message);
        }
    }
}
