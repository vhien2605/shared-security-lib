package vdt.mini.shared_lib.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vdt.mini.shared_lib.annotation.OutBoundSecurity;
import vdt.mini.shared_lib.document.OutboundEndpointDTO;
import vdt.mini.shared_lib.document.OutboundSettingsDTO;
import vdt.mini.shared_lib.enums.EndpointMethod;
import vdt.mini.shared_lib.enums.EndpointProtocol;
import vdt.mini.shared_lib.enums.OutboundErrorCode;
import vdt.mini.shared_lib.exception.OutboundException;
import vdt.mini.shared_lib.service.EndpointRegistry;
import vdt.mini.shared_lib.service.IdentityManager;
import vdt.mini.shared_lib.service.SecuritySettingsStore;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OutboundPolicyServiceTest {
    private EndpointRegistry endpointRegistry;
    private SecuritySettingsStore settingsStore;
    private OutboundPolicyService policyService;

    @BeforeEach
    void setUp() {
        endpointRegistry = new EndpointRegistry();
        settingsStore = mock(SecuritySettingsStore.class);
        IdentityManager identityManager = mock(IdentityManager.class);
        when(identityManager.getOrCreateServiceId()).thenReturn("service-1");
        policyService = new OutboundPolicyService(endpointRegistry, settingsStore, identityManager);
        endpointRegistry.replaceAll(List.of(), List.of(new OutboundEndpointDTO("endpoint-1", "Profile API",
                "http://profile/users", null, "GET", "HTTP", "", true)));
    }

    @Test
    void resolve_shouldBuildPolicy_whenSettingsValid() throws Exception {
        when(settingsStore.getOutboundSettings("endpoint-1")).thenReturn(validSettings());

        OutboundExecutionPolicy policy = policyService.resolve(annotation());

        assertThat(policy.endpointId()).isEqualTo("endpoint-1");
        assertThat(policy.retryCount()).isEqualTo(2);
        assertThat(policy.retryBackoffMs()).isEqualTo(0);
        assertThat(policy.timeoutMs()).isEqualTo(1000);
        assertThat(policy.rollbackStrategy()).isEqualTo("IGNORE");
    }

    @Test
    void resolve_shouldFallbackToName_whenTargetUrlDoesNotMatchRegistry() throws Exception {
        endpointRegistry.replaceAll(List.of(), List.of(new OutboundEndpointDTO("endpoint-1", "Profile API",
                "http://different", null, "GET", "HTTP", "", true)));
        when(settingsStore.getOutboundSettings("endpoint-1")).thenReturn(validSettings());

        assertThat(policyService.resolve(annotation()).endpointId()).isEqualTo("endpoint-1");
    }

    @Test
    void resolve_shouldFailClosed_whenSettingsMissing() throws Exception {
        when(settingsStore.getOutboundSettings("endpoint-1")).thenReturn(null);

        assertOutboundError(OutboundErrorCode.INTERNAL_ERROR);
    }

    @Test
    void resolve_shouldFail_whenEndpointDisabled() throws Exception {
        OutboundSettingsDTO settings = validSettings();
        settings.setEnabled(false);
        when(settingsStore.getOutboundSettings("endpoint-1")).thenReturn(settings);

        assertOutboundError(OutboundErrorCode.ENDPOINT_DISABLED);
    }

    @Test
    void resolve_shouldFail_whenEndpointInactive() throws Exception {
        OutboundSettingsDTO settings = validSettings();
        settings.setEndpointStatus("INACTIVE");
        when(settingsStore.getOutboundSettings("endpoint-1")).thenReturn(settings);

        assertOutboundError(OutboundErrorCode.ENDPOINT_INACTIVE);
    }

    @Test
    void resolve_shouldFail_whenServiceInactiveOrUnavailable() throws Exception {
        OutboundSettingsDTO inactive = validSettings();
        inactive.setServiceStatus("INACTIVE");
        when(settingsStore.getOutboundSettings("endpoint-1")).thenReturn(inactive);
        assertOutboundError(OutboundErrorCode.ENDPOINT_INACTIVE);

        OutboundSettingsDTO unavailable = validSettings();
        unavailable.setAvailable(false);
        when(settingsStore.getOutboundSettings("endpoint-1")).thenReturn(unavailable);
        assertOutboundError(OutboundErrorCode.ENDPOINT_INACTIVE);
    }

    @Test
    void resolve_shouldFail_whenProtocolOrMethodOrTargetMismatch() throws Exception {
        OutboundSettingsDTO protocolMismatch = validSettings();
        protocolMismatch.setProtocol("MQ");
        when(settingsStore.getOutboundSettings("endpoint-1")).thenReturn(protocolMismatch);
        assertOutboundError(OutboundErrorCode.INVALID_REQUEST);

        OutboundSettingsDTO methodMismatch = validSettings();
        methodMismatch.setMethod("POST");
        when(settingsStore.getOutboundSettings("endpoint-1")).thenReturn(methodMismatch);
        assertOutboundError(OutboundErrorCode.INVALID_REQUEST);

        OutboundSettingsDTO urlMismatch = validSettings();
        urlMismatch.setTargetUrl("http://other");
        when(settingsStore.getOutboundSettings("endpoint-1")).thenReturn(urlMismatch);
        assertOutboundError(OutboundErrorCode.INVALID_REQUEST);
    }

    @Test
    void resolve_shouldFail_whenExecutionPolicyMissingOrInvalid() throws Exception {
        OutboundSettingsDTO missingTimeout = validSettings();
        missingTimeout.setTimeoutMs(null);
        when(settingsStore.getOutboundSettings("endpoint-1")).thenReturn(missingTimeout);
        assertOutboundError(OutboundErrorCode.INVALID_REQUEST);

        OutboundSettingsDTO negativeRetry = validSettings();
        negativeRetry.setRetryCount(-1);
        when(settingsStore.getOutboundSettings("endpoint-1")).thenReturn(negativeRetry);
        assertOutboundError(OutboundErrorCode.INVALID_REQUEST);
    }

    private void assertOutboundError(OutboundErrorCode expected) throws Exception {
        assertThatThrownBy(() -> policyService.resolve(annotation()))
                .isInstanceOf(OutboundException.class)
                .extracting("errorCode")
                .isEqualTo(expected);
    }

    private OutBoundSecurity annotation() throws Exception {
        Method method = SampleOutbound.class.getDeclaredMethod("call");
        return method.getAnnotation(OutBoundSecurity.class);
    }

    private static OutboundSettingsDTO validSettings() {
        OutboundSettingsDTO settings = new OutboundSettingsDTO();
        settings.setEndpointId("endpoint-1");
        settings.setName("Profile API");
        settings.setTargetUrl("http://profile/users");
        settings.setMethod("GET");
        settings.setProtocol("HTTP");
        settings.setEnabled(true);
        settings.setEndpointStatus("ACTIVE");
        settings.setServiceStatus("ACTIVE");
        settings.setAvailable(true);
        settings.setTimeoutMs(1000);
        settings.setRetryCount(2);
        settings.setRetryBackoffMs(0);
        settings.setResponseTimeThresholdMs(10_000);
        settings.setRollbackStrategy("IGNORE");
        return settings;
    }

    static class SampleOutbound {
        @OutBoundSecurity(name = "Profile API", targetUrl = "http://profile/users", method = EndpointMethod.GET, protocol = EndpointProtocol.HTTP)
        void call() {
        }
    }
}
