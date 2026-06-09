package vdt.mini.shared_lib.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpResponse;
import vdt.mini.shared_lib.document.InboundSettingsDTO;
import vdt.mini.shared_lib.security.SecurityAuditLogger;
import vdt.mini.shared_lib.security.SecurityRequestContext;
import vdt.mini.shared_lib.security.SecurityRequestContextHolder;
import vdt.mini.shared_lib.security.SecurityStatusMapper;

import java.util.List;
import java.util.Map;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class InboundResponseSizeAdviceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final InboundResponseSizeAdvice advice = new InboundResponseSizeAdvice(
            objectMapper, new SecurityAuditLogger(objectMapper, new SecurityStatusMapper()));

    @AfterEach
    void tearDown() {
        SecurityRequestContextHolder.clear();
    }

    @Test
    void beforeBodyWrite_shouldReturn413Body_whenResponseSizeExceeded() {
        SecurityRequestContext context = new SecurityRequestContext();
        context.setInboundSettings(new InboundSettingsDTO("endpoint-1", "Endpoint", "/orders", null, "POST", "HTTP", true,
                "ACTIVE", "ACTIVE", true, null, null, null, null, 1, null, 30, null, null, null,
                List.of(), List.of(), List.of()));
        SecurityRequestContextHolder.set(context);
        CapturingResponse response = new CapturingResponse();

        Object result = advice.beforeBodyWrite("x".repeat(2048), null, null, null,
                null, response);

        assertThat(response.statusCode).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(result).isInstanceOf(Map.class);
        assertThat((Map<String, Object>) result).containsEntry("errorCode", "RESPONSE_SIZE_EXCEEDED");
    }

    private static class CapturingResponse implements ServerHttpResponse {
        private final HttpHeaders headers = new HttpHeaders();
        private final ByteArrayOutputStream body = new ByteArrayOutputStream();
        private HttpStatusCode statusCode;

        @Override
        public void setStatusCode(HttpStatusCode status) {
            this.statusCode = status;
        }

        @Override
        public OutputStream getBody() throws IOException {
            return body;
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
