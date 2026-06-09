package vdt.mini.shared_lib.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import vdt.mini.shared_lib.document.InboundEndpointDTO;
import vdt.mini.shared_lib.enums.SecurityErrorCode;
import vdt.mini.shared_lib.enums.SecurityResultStatus;
import vdt.mini.shared_lib.security.InboundSecurityDecisionService;
import vdt.mini.shared_lib.security.SecurityAuditLogger;
import vdt.mini.shared_lib.security.SecurityDecision;
import vdt.mini.shared_lib.security.SecurityRequestContext;
import vdt.mini.shared_lib.security.SecurityStatusMapper;
import vdt.mini.shared_lib.service.EndpointRegistry;
import vdt.mini.shared_lib.service.IdentityManager;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityAuthFilterTest {
    private static final String SERVICE_ID = "service-1";
    private static final String ENDPOINT_ID = "endpoint-1";

    @Mock
    private InboundSecurityDecisionService decisionService;
    @Mock
    private SecurityAuditLogger auditLogger;
    @Mock
    private IdentityManager identityManager;

    private EndpointRegistry endpointRegistry;
    private SecurityAuthFilter filter;

    @BeforeEach
    void setUp() {
        endpointRegistry = new EndpointRegistry();
        filter = new SecurityAuthFilter(endpointRegistry, decisionService, new SecurityStatusMapper(), auditLogger,
                identityManager, new ObjectMapper(), "user-service");
        when(identityManager.getOrCreateServiceId()).thenReturn(SERVICE_ID);
    }

    @Test
    void doFilterInternal_shouldPassThroughWithoutDecision_whenEndpointNotRegistered() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/public/ping");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(response.getStatus()).isEqualTo(200);
        verifyNoInteractions(decisionService, auditLogger);
    }

    @Test
    void doFilterInternal_shouldStripContextPathBeforeRegistryLookup() throws ServletException, IOException {
        registerWebhookEndpoint();
        MockHttpServletRequest request = requestWithContextPath();
        when(decisionService.decide(eq(request), any(), any())).thenReturn(SecurityDecision.allow(ENDPOINT_ID, null, null));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        ArgumentCaptor<EndpointRegistry.InboundHttpEndpoint> endpointCaptor = ArgumentCaptor.forClass(EndpointRegistry.InboundHttpEndpoint.class);
        ArgumentCaptor<SecurityRequestContext> contextCaptor = ArgumentCaptor.forClass(SecurityRequestContext.class);
        verify(decisionService).decide(eq(request), endpointCaptor.capture(), contextCaptor.capture());
        assertThat(endpointCaptor.getValue().endpointId()).isEqualTo(ENDPOINT_ID);
        assertThat(contextCaptor.getValue().getPath()).isEqualTo("/users/webhook");
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void doFilterInternal_shouldReturnUnauthorized_whenRegisteredEndpointMissingAuth() throws ServletException, IOException {
        registerWebhookEndpoint();
        when(decisionService.decide(any(), any(), any()))
                .thenReturn(SecurityDecision.deny(SecurityResultStatus.DENIED, SecurityErrorCode.AUTH_MISSING,
                        "Missing client key", ENDPOINT_ID, null, null));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/users/webhook");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("AUTH_MISSING");
    }

    private void registerWebhookEndpoint() {
        endpointRegistry.replaceAll(List.of(new InboundEndpointDTO(ENDPOINT_ID, "Webhook", "/users/webhook", null,
                "POST", "HTTP", "", true)), List.of());
    }

    private MockHttpServletRequest requestWithContextPath() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/app/users/webhook");
        request.setContextPath("/app");
        request.setRemoteAddr("127.0.0.1");
        return request;
    }
}
