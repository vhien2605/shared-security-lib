package vdt.mini.shared_lib.web;

import org.springframework.stereotype.Service;
import vdt.mini.shared_lib.annotation.OutBoundSecurity;
import vdt.mini.shared_lib.document.OutboundSettingsDTO;
import vdt.mini.shared_lib.enums.OutboundErrorCode;
import vdt.mini.shared_lib.enums.EndpointProtocol;
import vdt.mini.shared_lib.exception.OutboundException;
import vdt.mini.shared_lib.service.EndpointRegistry;
import vdt.mini.shared_lib.service.IdentityManager;
import vdt.mini.shared_lib.service.SecuritySettingsStore;

import java.util.Locale;

@Service
public class OutboundPolicyService {
    private static final String ACTIVE = "ACTIVE";

    private final EndpointRegistry endpointRegistry;
    private final SecuritySettingsStore settingsStore;
    private final IdentityManager identityManager;

    public OutboundPolicyService(EndpointRegistry endpointRegistry, SecuritySettingsStore settingsStore, IdentityManager identityManager) {
        this.endpointRegistry = endpointRegistry;
        this.settingsStore = settingsStore;
        this.identityManager = identityManager;
    }

    public OutboundExecutionPolicy resolve(OutBoundSecurity annotation) {
        if (annotation == null) {
            throw new OutboundException(OutboundErrorCode.INVALID_REQUEST, "Outbound security annotation is required");
        }
        String serviceId = identityManager.getOrCreateServiceId();
        String protocol = annotation.protocol().name();
        String method = annotation.method().name();
        EndpointRegistry.OutboundEndpoint endpoint = resolveEndpoint(annotation, serviceId, protocol, method);

        OutboundSettingsDTO settings = settingsStore.getOutboundSettings(endpoint.endpointId());
        validateSettings(endpoint, settings, protocol, method, annotation.targetUrl(), annotation.topic());
        return new OutboundExecutionPolicy(
                endpoint.endpointId(),
                nonBlank(settings.getName(), endpoint.name()),
                serviceId,
                nonBlank(settings.getTargetUrl(), annotation.targetUrl()),
                nonBlank(settings.getTopic(), annotation.topic()),
                normalize(settings.getMethod()),
                normalize(settings.getProtocol()),
                settings.getTimeoutMs(),
                settings.getRetryCount() == null ? 0 : settings.getRetryCount(),
                settings.getRetryBackoffMs() == null ? 0 : settings.getRetryBackoffMs(),
                settings.getResponseTimeThresholdMs(),
                settings.getLogRetentionDays(),
                normalizeRollbackStrategy(settings.getRollbackStrategy()),
                settings.getAlertSeverity(),
                settings.getAlertThrottleMinutes(),
                settings.getAlertChannels() == null ? java.util.List.of() : java.util.List.copyOf(settings.getAlertChannels()),
                settings);
    }

    private EndpointRegistry.OutboundEndpoint resolveEndpoint(OutBoundSecurity annotation, String serviceId,
                                                              String protocol, String method) {
        if (annotation.protocol() == EndpointProtocol.MQ) {
            return endpointRegistry
                    .findOutBoundMq(serviceId, protocol, method, annotation.topic(), annotation.name())
                    .orElseThrow(() -> new OutboundException(OutboundErrorCode.ENDPOINT_NOT_REGISTERED,
                            "Outbound MQ endpoint is not registered"));
        }
        return endpointRegistry
                .findOutBoundHttp(serviceId, protocol, method, annotation.targetUrl(), annotation.name())
                .orElseThrow(() -> new OutboundException(OutboundErrorCode.ENDPOINT_NOT_REGISTERED,
                        "Outbound endpoint is not registered"));
    }

    private void validateSettings(EndpointRegistry.OutboundEndpoint endpoint, OutboundSettingsDTO settings,
                                  String expectedProtocol, String expectedMethod, String expectedTargetUrl,
                                  String expectedTopic) {
        if (settings == null) {
            throw new OutboundException(OutboundErrorCode.INTERNAL_ERROR, "Outbound settings are missing", null, endpoint.endpointId());
        }
        if (!Boolean.TRUE.equals(settings.getEnabled())) {
            throw new OutboundException(OutboundErrorCode.ENDPOINT_DISABLED, "Outbound endpoint is disabled", null, endpoint.endpointId());
        }
        if (!ACTIVE.equals(normalize(settings.getEndpointStatus()))) {
            throw new OutboundException(OutboundErrorCode.ENDPOINT_INACTIVE, "Outbound endpoint is inactive", null, endpoint.endpointId());
        }
        if (!ACTIVE.equals(normalize(settings.getServiceStatus()))) {
            throw new OutboundException(OutboundErrorCode.ENDPOINT_INACTIVE, "Outbound service is inactive", null, endpoint.endpointId());
        }
        if (!Boolean.TRUE.equals(settings.getAvailable())) {
            throw new OutboundException(OutboundErrorCode.ENDPOINT_INACTIVE, "Outbound service is unavailable", null, endpoint.endpointId());
        }
        if (!normalize(expectedProtocol).equals(normalize(settings.getProtocol()))) {
            throw new OutboundException(OutboundErrorCode.INVALID_REQUEST, "Outbound protocol mismatch", null, endpoint.endpointId());
        }
        if (!normalize(expectedMethod).equals(normalize(settings.getMethod()))) {
            throw new OutboundException(OutboundErrorCode.INVALID_REQUEST, "Outbound method mismatch", null, endpoint.endpointId());
        }
        if (hasText(expectedTargetUrl) && hasText(settings.getTargetUrl()) && !settings.getTargetUrl().trim().equalsIgnoreCase(expectedTargetUrl.trim())) {
            throw new OutboundException(OutboundErrorCode.INVALID_REQUEST, "Outbound targetUrl mismatch", null, endpoint.endpointId());
        }
        if (hasText(expectedTopic) && hasText(settings.getTopic()) && !settings.getTopic().trim().equalsIgnoreCase(expectedTopic.trim())) {
            throw new OutboundException(OutboundErrorCode.INVALID_REQUEST, "Outbound topic mismatch", null, endpoint.endpointId());
        }
        if (settings.getTimeoutMs() == null || settings.getTimeoutMs() <= 0
                || (settings.getRetryCount() != null && settings.getRetryCount() < 0)
                || (settings.getRetryBackoffMs() != null && settings.getRetryBackoffMs() < 0)) {
            throw new OutboundException(OutboundErrorCode.INVALID_REQUEST, "Outbound execution policy is invalid", null, endpoint.endpointId());
        }
    }

    private static String normalizeRollbackStrategy(String value) {
        String normalized = normalize(value);
        if ("COMPESATE".equals(normalized)) {
            return "COMPENSATE";
        }
        return hasText(normalized) ? normalized : "IGNORE";
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String nonBlank(String value, String fallback) {
        return hasText(value) ? value : fallback;
    }
}
