package vdt.mini.shared_lib.mq;

public record MqSecurityRequest(
        String topic,
        String key,
        Object value,
        MqSecurityHeaders headers,
        long messageSizeBytes) {
}
