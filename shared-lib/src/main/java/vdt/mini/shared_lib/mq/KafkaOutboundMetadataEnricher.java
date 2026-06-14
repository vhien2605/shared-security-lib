package vdt.mini.shared_lib.mq;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import vdt.mini.shared_lib.web.OutboundContext;
import vdt.mini.shared_lib.web.OutboundContextHolder;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Explicit helper for outbound MQ wrappers to propagate existing tracing headers without auth/signature metadata.
 */
public class KafkaOutboundMetadataEnricher {
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final Set<String> CONTROL_PLANE_TOPICS = Set.of("security.endpoint.registration");

    private final OutboundContextHolder contextHolder;

    public KafkaOutboundMetadataEnricher(OutboundContextHolder contextHolder) {
        this.contextHolder = contextHolder;
    }

    public <K, V> ProducerRecord<K, V> enrich(ProducerRecord<K, V> record) {
        if (record == null || CONTROL_PLANE_TOPICS.contains(record.topic())) {
            return record;
        }
        OutboundContext context = contextHolder.get();
        if (context == null) {
            return record;
        }
        addIfPresent(record.headers(), TRACE_ID_HEADER, context.traceId());
        addIfPresent(record.headers(), CORRELATION_ID_HEADER, context.correlationId());
        return record;
    }

    private static void addIfPresent(Headers headers, String key, String value) {
        if (value != null && !value.isBlank()) {
            headers.remove(key);
            headers.add(key, value.getBytes(StandardCharsets.UTF_8));
        }
    }
}
