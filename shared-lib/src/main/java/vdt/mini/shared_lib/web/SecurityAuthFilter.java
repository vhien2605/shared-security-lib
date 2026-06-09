package vdt.mini.shared_lib.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import vdt.mini.shared_lib.security.InboundSecurityDecisionService;
import vdt.mini.shared_lib.security.SecurityAuditLogger;
import vdt.mini.shared_lib.security.SecurityDecision;
import vdt.mini.shared_lib.enums.SecurityErrorCode;
import vdt.mini.shared_lib.security.SecurityRequestContext;
import vdt.mini.shared_lib.security.SecurityRequestContextHolder;
import vdt.mini.shared_lib.enums.SecurityResultStatus;
import vdt.mini.shared_lib.security.SecurityStatusMapper;
import vdt.mini.shared_lib.service.EndpointRegistry;
import vdt.mini.shared_lib.service.IdentityManager;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class SecurityAuthFilter extends OncePerRequestFilter {
    private final EndpointRegistry endpointRegistry;
    private final InboundSecurityDecisionService decisionService;
    private final SecurityStatusMapper statusMapper;
    private final SecurityAuditLogger auditLogger;
    private final IdentityManager identityManager;
    private final ObjectMapper objectMapper;
    private final String serviceName;

    public SecurityAuthFilter(EndpointRegistry endpointRegistry,
                              InboundSecurityDecisionService decisionService,
                              SecurityStatusMapper statusMapper,
                              SecurityAuditLogger auditLogger,
                              IdentityManager identityManager,
                              ObjectMapper objectMapper,
                              @Value("${app.security.service.name:my-service}") String serviceName) {
        this.endpointRegistry = endpointRegistry;
        this.decisionService = decisionService;
        this.statusMapper = statusMapper;
        this.auditLogger = auditLogger;
        this.identityManager = identityManager;
        this.objectMapper = objectMapper;
        this.serviceName = serviceName;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        SecurityRequestContext context = buildContext(request);
        try {
            Optional<EndpointRegistry.InboundHttpEndpoint> endpoint = endpointRegistry.findInboundHttp(request.getMethod(), lookupPath(request));
            if (endpoint.isEmpty()) {
                filterChain.doFilter(request, response);
                return;
            }
            SecurityDecision decision = decisionService.decide(request, endpoint.get(), context);
            if (!decision.allowed()) {
                auditLogger.log(context, decision.status(), decision.errorCode());
                writeError(response, decision.errorCode(), decision.message());
                return;
            }
            context.setEndpointId(decision.endpointId() == null ? context.getEndpointId() : decision.endpointId());
            context.setClientId(decision.clientId());
            context.setClientKey(decision.clientKey());
            SecurityRequestContextHolder.set(context);
            filterChain.doFilter(request, response);
        } finally {
            SecurityRequestContextHolder.clear();
        }
    }

    private String lookupPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && requestUri != null && requestUri.startsWith(contextPath)) {
            String withoutContext = requestUri.substring(contextPath.length());
            return withoutContext.isBlank() ? "/" : withoutContext;
        }
        return requestUri;
    }

    private SecurityRequestContext buildContext(HttpServletRequest request) {
        SecurityRequestContext context = new SecurityRequestContext();
        context.setTraceId(firstNonBlank(request.getHeader("X-Trace-Id"), UUID.randomUUID().toString()));
        context.setCorrelationId(firstNonBlank(request.getHeader("X-Correlation-Id"), context.getTraceId()));
        context.setServiceId(identityManager.getOrCreateServiceId());
        context.setServiceName(serviceName);
        context.setMethod(request.getMethod());
        context.setPath(lookupPath(request));
        context.setSourceIp(firstNonBlank(request.getHeader("X-Forwarded-For"), request.getRemoteAddr()));
        context.setRequestSizeBytes(Math.max(0L, request.getContentLengthLong()));
        context.setStartedAtNanos(System.nanoTime());
        return context;
    }

    private void writeError(HttpServletResponse response, SecurityErrorCode errorCode, String message) throws IOException {
        response.setStatus(statusMapper.toHttpStatus(errorCode).value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", SecurityResultStatus.DENIED.name());
        body.put("resultCode", statusMapper.resultCode(errorCode));
        body.put("errorCode", errorCode.name());
        body.put("message", message);
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
    }
}
