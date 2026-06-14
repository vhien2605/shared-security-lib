package vdt.mini.shared_lib.web;

import java.time.Instant;

public record OutboundContext(
        String serviceId,
        String endpointId,
        String endpointName,
        String targetUrl,
        String method,
        String protocol,
        String traceId,
        String correlationId,
        Instant timestamp,
        String nonce) {
}
