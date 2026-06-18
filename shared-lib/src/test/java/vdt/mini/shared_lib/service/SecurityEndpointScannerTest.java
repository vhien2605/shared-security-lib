package vdt.mini.shared_lib.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;
import vdt.mini.shared_lib.annotation.InBoundSecurity;
import vdt.mini.shared_lib.annotation.OutBoundSecurity;
import vdt.mini.shared_lib.document.InboundEndpointDTO;
import vdt.mini.shared_lib.document.ServiceRegistrationEvent;
import vdt.mini.shared_lib.enums.EndpointMethod;
import vdt.mini.shared_lib.enums.EndpointProtocol;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecurityEndpointScannerTest {

    private static final String NAMESPACE = "mini-project";
    private static final String SERVICE_NAME = "user-service";
    private static final String BASE_URL = "http://localhost:8081";
    private static final String DESCRIPTION = "User Service";

    @TempDir
    Path tempDir;

    @Test
    void onApplicationReady_registrarMode_shouldPublishRegistryAndPollRedisWithoutWritingIdentity() {
        KafkaPublisher kafkaPublisher = mock(KafkaPublisher.class);
        SecuritySettingsStore settingsStore = mock(SecuritySettingsStore.class);
        EndpointRegistry endpointRegistry = new EndpointRegistry();
        SecurityEndpointScanner scanner = scanner(kafkaPublisher, settingsStore, endpointRegistry, new AnnotatedBean(), true);
        Path identityPath = tempDir.resolve("identity.json");

        scanner.onApplicationReady();

        String serviceId = SecurityIdGenerator.serviceId(NAMESPACE, SERVICE_NAME);
        String inboundId = SecurityIdGenerator.endpointId(serviceId, "INBOUND", "HTTP", "GET", "/users/{id}", "");
        String outboundId = SecurityIdGenerator.endpointId(serviceId, "OUTBOUND", "MQ", "PUB", "profile.request", "");

        org.mockito.ArgumentCaptor<ServiceRegistrationEvent> eventCaptor = org.mockito.ArgumentCaptor.forClass(ServiceRegistrationEvent.class);
        verify(kafkaPublisher).send(eq("security.endpoint.registration"), eventCaptor.capture());
        ServiceRegistrationEvent event = eventCaptor.getValue();
        assertThat(event.getServiceId()).isEqualTo(serviceId);
        assertThat(event.getServiceName()).isEqualTo(SERVICE_NAME);
        assertThat(event.getBaseUrl()).isEqualTo(BASE_URL);
        assertThat(event.getDescription()).isEqualTo(DESCRIPTION);
        assertThat(event.getInbounds()).extracting(InboundEndpointDTO::getEndpointId).containsExactly(inboundId);
        assertThat(event.getOutbounds()).extracting("endpointId").containsExactly(outboundId);
        assertThat(endpointRegistry.findInboundHttp("GET", "/users/123")).isPresent()
                .get().extracting(EndpointRegistry.InboundHttpEndpoint::endpointId).isEqualTo(inboundId);
        assertThat(endpointRegistry.outboundEndpoints()).extracting(EndpointRegistry.OutboundEndpoint::endpointId).containsExactly(outboundId);
        verify(settingsStore).pollRuntimeFromRedis(eq(serviceId), eq(List.of(inboundId)), eq(List.of(outboundId)));
        assertThat(Files.exists(identityPath)).isFalse();
    }

    @Test
    void onApplicationReady_followerMode_shouldNotPublishButStillRegistryAndPollRedis() {
        KafkaPublisher kafkaPublisher = mock(KafkaPublisher.class);
        SecuritySettingsStore settingsStore = mock(SecuritySettingsStore.class);
        EndpointRegistry endpointRegistry = new EndpointRegistry();
        SecurityEndpointScanner scanner = scanner(kafkaPublisher, settingsStore, endpointRegistry, new AnnotatedBean(), false);

        scanner.onApplicationReady();

        String serviceId = SecurityIdGenerator.serviceId(NAMESPACE, SERVICE_NAME);
        String inboundId = SecurityIdGenerator.endpointId(serviceId, "INBOUND", "HTTP", "GET", "/users/{id}", "");
        String outboundId = SecurityIdGenerator.endpointId(serviceId, "OUTBOUND", "MQ", "PUB", "profile.request", "");

        verify(kafkaPublisher, never()).send(any(), any());
        assertThat(endpointRegistry.findInboundHttp("GET", "/users/123")).isPresent();
        assertThat(endpointRegistry.outboundEndpoints()).extracting(EndpointRegistry.OutboundEndpoint::endpointId).containsExactly(outboundId);
        verify(settingsStore).pollRuntimeFromRedis(eq(serviceId), eq(List.of(inboundId)), eq(List.of(outboundId)));
    }

    @Test
    void onApplicationReady_shouldSkipDuplicateCanonicalEndpoint() {
        KafkaPublisher kafkaPublisher = mock(KafkaPublisher.class);
        SecuritySettingsStore settingsStore = mock(SecuritySettingsStore.class);
        EndpointRegistry endpointRegistry = new EndpointRegistry();
        SecurityEndpointScanner scanner = scanner(kafkaPublisher, settingsStore, endpointRegistry, new DuplicateAnnotatedBean(), false);

        scanner.onApplicationReady();

        assertThat(endpointRegistry.findInboundHttp("GET", "/duplicate")).isPresent();
        verify(settingsStore).pollRuntimeFromRedis(
                eq(SecurityIdGenerator.serviceId(NAMESPACE, SERVICE_NAME)),
                eq(List.of(SecurityIdGenerator.endpointId(SecurityIdGenerator.serviceId(NAMESPACE, SERVICE_NAME), "INBOUND", "HTTP", "GET", "/duplicate", ""))),
                eq(List.of()));
    }

    private SecurityEndpointScanner scanner(KafkaPublisher kafkaPublisher,
                                            SecuritySettingsStore settingsStore,
                                            EndpointRegistry endpointRegistry,
                                            Object bean,
                                            boolean registrationEnabled) {
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getBeanDefinitionNames()).thenReturn(new String[]{"testBean"});
        when(applicationContext.getBean("testBean")).thenReturn(bean);
        SecurityEndpointScanner scanner = new SecurityEndpointScanner(applicationContext, kafkaPublisher, settingsStore, endpointRegistry);
        ReflectionTestUtils.setField(scanner, "namespace", NAMESPACE);
        ReflectionTestUtils.setField(scanner, "serviceName", SERVICE_NAME);
        ReflectionTestUtils.setField(scanner, "baseUrl", BASE_URL);
        ReflectionTestUtils.setField(scanner, "serviceDescription", DESCRIPTION);
        ReflectionTestUtils.setField(scanner, "registrationTopic", "security.endpoint.registration");
        ReflectionTestUtils.setField(scanner, "enabled", true);
        ReflectionTestUtils.setField(scanner, "syncEnabled", true);
        ReflectionTestUtils.setField(scanner, "registrationEnabled", registrationEnabled);
        return scanner;
    }

    static class AnnotatedBean {
        @InBoundSecurity(name = "get user", path = "/users/{id}", method = EndpointMethod.GET, protocol = EndpointProtocol.HTTP)
        public void inbound() {
        }

        @OutBoundSecurity(name = "profile request", topic = "profile.request", method = EndpointMethod.PUB, protocol = EndpointProtocol.MQ)
        public void outbound() {
        }
    }

    static class DuplicateAnnotatedBean {
        @InBoundSecurity(name = "first", path = "/duplicate", method = EndpointMethod.GET, protocol = EndpointProtocol.HTTP)
        public void first() {
        }

        @InBoundSecurity(name = "second", path = "/duplicate", method = EndpointMethod.GET, protocol = EndpointProtocol.HTTP)
        public void second() {
        }
    }
}
