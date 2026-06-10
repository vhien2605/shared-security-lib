package vdt.mini.profile_service.controller;

public record PublishRequest(
        String clientKey,
        String apiKey,
        String authType,
        String secretKey,
        String value,
        String correlationId,
        String traceId) {
}
