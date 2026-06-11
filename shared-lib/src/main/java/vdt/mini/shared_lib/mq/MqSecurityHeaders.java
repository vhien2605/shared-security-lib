package vdt.mini.shared_lib.mq;

public record MqSecurityHeaders(
        String clientKey,
        String apiKey,
        String signature,
        String timestamp,
        String nonce,
        String correlationId,
        String traceId) {
}
