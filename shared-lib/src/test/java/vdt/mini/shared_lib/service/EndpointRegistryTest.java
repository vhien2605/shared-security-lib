package vdt.mini.shared_lib.service;

import org.junit.jupiter.api.Test;
import vdt.mini.shared_lib.document.InboundEndpointDTO;

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
}
