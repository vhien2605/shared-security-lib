package vdt.mini.shared_lib.service;

import org.junit.jupiter.api.Test;
import vdt.mini.shared_lib.document.InboundEndpointDTO;
import vdt.mini.shared_lib.document.OutboundEndpointDTO;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EndpointRegistryTest {
    @Test
    void findInboundHttp_shouldMatchMethodAndPathPattern() {
        EndpointRegistry registry = new EndpointRegistry();
        registry.replaceAll(List.of(new InboundEndpointDTO("endpoint-1", "Get User", "/users/{id}", null,
                "GET", "HTTP", "", true)), List.of());

        assertThat(registry.findInboundHttp("GET", "/users/123"))
                .isPresent()
                .get()
                .extracting(EndpointRegistry.InboundHttpEndpoint::endpointId)
                .isEqualTo("endpoint-1");
        assertThat(registry.findInboundHttp("POST", "/users/123")).isEmpty();
        assertThat(registry.findInboundHttp("GET", "/orders/123")).isEmpty();
    }

    @Test
    void findInboundMq_shouldMatchTopicOnlyForMqEndpoints() {
        EndpointRegistry registry = new EndpointRegistry();
        registry.replaceAll(List.of(new InboundEndpointDTO("endpoint-1", "User Created", null, "user.created",
                null, "MQ", "", true)), List.of());

        assertThat(registry.findInboundMq("user.created"))
                .isPresent()
                .get()
                .extracting(EndpointRegistry.InboundMqEndpoint::endpointId)
                .isEqualTo("endpoint-1");
        assertThat(registry.findInboundMq("user.deleted")).isEmpty();
    }

    @Test
    void findOutbound_shouldMatchDestinationAndFallbackByName() {
        EndpointRegistry registry = new EndpointRegistry();
        registry.replaceAll(List.of(), List.of(new OutboundEndpointDTO("endpoint-1", "Profile API", "http://profile/users",
                null, "GET", "HTTP", "", true)));

        assertThat(registry.findOutBoundHttp("service-1", "HTTP", "GET", "http://profile/users", "Other"))
                .isPresent()
                .get()
                .extracting(EndpointRegistry.OutboundEndpoint::endpointId)
                .isEqualTo("endpoint-1");
        assertThat(registry.findOutBoundHttp("service-1", "HTTP", "GET", "http://different", "Profile API"))
                .isPresent();
        assertThat(registry.findOutBoundHttp("service-1", "HTTP", "POST", "http://profile/users", "Profile API"))
                .isEmpty();
    }
}
