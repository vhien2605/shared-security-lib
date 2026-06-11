package vdt.mini.shared_lib.mq;

import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;
import vdt.mini.shared_lib.security.InboundSecurityDecisionService;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MqSecurityHeaderExtractorTest {
    private final MqSecurityHeaderExtractor extractor = new MqSecurityHeaderExtractor();

    @Test
    void extract_shouldReadCanonicalAndLowercaseHeaders() {
        RecordHeaders headers = new RecordHeaders();
        headers.add(InboundSecurityDecisionService.CLIENT_KEY_HEADER.toLowerCase(), bytes("client-key"));
        headers.add(InboundSecurityDecisionService.API_KEY_HEADER, bytes("api-key"));
        headers.add(InboundSecurityDecisionService.SIGNATURE_HEADER, bytes("signature"));
        headers.add(InboundSecurityDecisionService.TIMESTAMP_HEADER, bytes("123"));
        headers.add(InboundSecurityDecisionService.NONCE_HEADER, bytes("nonce"));
        headers.add(MqSecurityHeaderExtractor.CORRELATION_ID_HEADER, bytes("corr"));

        MqSecurityHeaders extracted = extractor.extract(headers);

        assertThat(extracted.clientKey()).isEqualTo("client-key");
        assertThat(extracted.apiKey()).isEqualTo("api-key");
        assertThat(extracted.signature()).isEqualTo("signature");
        assertThat(extracted.timestamp()).isEqualTo("123");
        assertThat(extracted.nonce()).isEqualTo("nonce");
        assertThat(extracted.correlationId()).isEqualTo("corr");
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
