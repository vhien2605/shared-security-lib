package vdt.mini.shared_lib.security;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import vdt.mini.shared_lib.document.OutboundSettingsDTO;
import vdt.mini.shared_lib.enums.SecurityResultStatus;
import vdt.mini.shared_lib.web.OutboundContext;
import vdt.mini.shared_lib.web.OutboundExecutionPolicy;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityAuditLoggerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private Logger auditLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        auditLogger = (Logger) LoggerFactory.getLogger("SECURITY_AUDIT");
        appender = new ListAppender<>();
        appender.start();
        auditLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        auditLogger.detachAppender(appender);
    }

    @Test
    void logOutbound_shouldIncludeTraceAndCorrelationIdsFromOutboundContext() throws Exception {
        SecurityAuditLogger logger = new SecurityAuditLogger(objectMapper, new SecurityStatusMapper());
        OutboundContext context = new OutboundContext("service-1", "endpoint-1", "Profile API", "http://profile/users",
                "GET", "HTTP", "trace-1", "corr-1", Instant.now(), "nonce-1");

        logger.logOutbound(policy(), context, SecurityResultStatus.SUCCESS, null, 12, 0);

        assertThat(appender.list).hasSize(1);
        JsonNode event = objectMapper.readTree(appender.list.getFirst().getFormattedMessage());
        assertThat(event.get("traceId").asText()).isEqualTo("trace-1");
        assertThat(event.get("correlationId").asText()).isEqualTo("corr-1");
        assertThat(event.get("flowType").asText()).isEqualTo("OUTBOUND_HTTP");
    }

    private OutboundExecutionPolicy policy() {
        return new OutboundExecutionPolicy("endpoint-1", "Profile API", "service-1", "http://profile/users", null,
                "GET", "HTTP", 1000, 1, 0, null, 30, "IGNORE", null, null,
                List.of(), new OutboundSettingsDTO());
    }
}
