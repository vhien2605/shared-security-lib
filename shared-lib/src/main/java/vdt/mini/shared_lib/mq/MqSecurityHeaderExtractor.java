package vdt.mini.shared_lib.mq;

import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.springframework.stereotype.Component;
import vdt.mini.shared_lib.security.InboundSecurityDecisionService;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Component
public class MqSecurityHeaderExtractor {
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    public MqSecurityHeaders extract(Headers headers) {
        return new MqSecurityHeaders(
                value(headers, InboundSecurityDecisionService.CLIENT_KEY_HEADER),
                value(headers, InboundSecurityDecisionService.API_KEY_HEADER),
                value(headers, InboundSecurityDecisionService.SIGNATURE_HEADER),
                value(headers, InboundSecurityDecisionService.TIMESTAMP_HEADER),
                value(headers, InboundSecurityDecisionService.NONCE_HEADER),
                value(headers, CORRELATION_ID_HEADER),
                value(headers, TRACE_ID_HEADER));
    }

    private String value(Headers headers, String name) {
        if (headers == null || name == null || name.isBlank()) {
            return null;
        }
        Header exact = headers.lastHeader(name);
        if (exact != null) {
            return decode(exact);
        }
        String lowercase = name.toLowerCase(Locale.ROOT);
        for (Header header : headers) {
            if (header != null && header.key() != null && header.key().toLowerCase(Locale.ROOT).equals(lowercase)) {
                return decode(header);
            }
        }
        return null;
    }

    private String decode(Header header) {
        byte[] value = header.value();
        return value == null ? null : new String(value, StandardCharsets.UTF_8);
    }
}
