package vdt.mini.management_service.dto.event;

import java.time.Instant;

public record RollingWindowEntry(Instant timestamp,
                                 String status,
                                 Long durationMs,
                                 Long requestSizeBytes,
                                 Long responseSizeBytes,
                                 Long messageSizeBytes,
                                 Integer retryAttempt,
                                 String clientId,
                                 String sourceIp,
                                 String errorCode,
                                 String denyReason) {
    public static RollingWindowEntry from(SecurityLogEventMessage event) {
        return new RollingWindowEntry(Instant.parse(event.getTimestamp()), event.getStatus(), event.getDurationMs(),
                event.getRequestSizeBytes(), event.getResponseSizeBytes(), event.getMessageSizeBytes(),
                event.getRetryAttempt(), event.getClientId(), event.getSourceIp(), event.getErrorCode(), event.getDenyReason());
    }
}
