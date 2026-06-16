package vdt.mini.shared_lib.mq;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vdt.mini.shared_lib.web.OutboundContext;
import vdt.mini.shared_lib.web.OutboundContextHolder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaOutboundMetadataEnricherTest {
    private OutboundContextHolder contextHolder;
    private KafkaOutboundMetadataEnricher enricher;

    @BeforeEach
    void setUp() {
        contextHolder = new OutboundContextHolder();
        enricher = new KafkaOutboundMetadataEnricher(contextHolder);
        contextHolder.clear();
    }

    @Test
    void enrich_shouldNoOp_whenContextMissing() {
        ProducerRecord<String, String> record = new ProducerRecord<>("user.created", "payload");

        enricher.enrich(record);

        assertThat(record.headers().lastHeader("X-Trace-Id")).isNull();
        assertThat(record.headers().lastHeader("X-Correlation-Id")).isNull();
        assertForbiddenHeadersMissing(record);
    }

    @Test
    void enrich_shouldPropagateExistingTraceAndCorrelationOnly() {
        contextHolder.set(new OutboundContext("service-1", "endpoint-1", "User Created", null,
                "PUB", "MQ", "trace-1", "corr-1", Instant.now(), "nonce"));
        ProducerRecord<String, String> record = new ProducerRecord<>("user.created", "payload");

        enricher.enrich(record);

        assertThat(headerValue(record, "X-Trace-Id")).isEqualTo("trace-1");
        assertThat(headerValue(record, "X-Correlation-Id")).isEqualTo("corr-1");
        assertForbiddenHeadersMissing(record);
    }

    @Test
    void enrich_shouldNotGenerateMetadata_whenValuesBlank() {
        contextHolder.set(new OutboundContext("service-1", "endpoint-1", "User Created", null,
                "PUB", "MQ", null, "", Instant.now(), "nonce"));
        ProducerRecord<String, String> record = new ProducerRecord<>("user.created", "payload");

        enricher.enrich(record);

        assertThat(record.headers().lastHeader("X-Trace-Id")).isNull();
        assertThat(record.headers().lastHeader("X-Correlation-Id")).isNull();
    }

    @Test
    void enrich_shouldNoOpForRegistrationControlPlaneTopic() {
        contextHolder.set(new OutboundContext("service-1", "endpoint-1", "Registration", null,
                "PUB", "MQ", "trace-1", "corr-1", Instant.now(), "nonce"));
        ProducerRecord<String, String> record = new ProducerRecord<>("security.endpoint.registration", "payload");

        enricher.enrich(record);

        assertThat(record.headers().lastHeader("X-Trace-Id")).isNull();
        assertThat(record.headers().lastHeader("X-Correlation-Id")).isNull();
        assertForbiddenHeadersMissing(record);
    }

    @Test
    void enrich_shouldNoOpForSecurityAuditTopic() {
        contextHolder.set(new OutboundContext("service-1", "endpoint-1", "Audit", null,
                "PUB", "MQ", "trace-1", "corr-1", Instant.now(), "nonce"));
        ProducerRecord<String, String> record = new ProducerRecord<>("security.logs", "payload");

        enricher.enrich(record);

        assertThat(record.headers().lastHeader("X-Trace-Id")).isNull();
        assertThat(record.headers().lastHeader("X-Correlation-Id")).isNull();
        assertForbiddenHeadersMissing(record);
    }

    private static String headerValue(ProducerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private static void assertForbiddenHeadersMissing(ProducerRecord<String, String> record) {
        assertThat(record.headers().lastHeader("X-Client-Key")).isNull();
        assertThat(record.headers().lastHeader("X-Signature")).isNull();
        assertThat(record.headers().lastHeader("Authorization")).isNull();
    }
}
