package vdt.mini.management_service.service.anomaly.runtime;

import org.junit.jupiter.api.Test;
import vdt.mini.management_service.dto.event.StaticResultContext;

import static org.junit.jupiter.api.Assertions.*;

class StaticResultContextParserTest {
    @Test
    void parse_shouldNormalizeStaticSignalsWithoutCreatingAnomalyTypes() {
        var event = AnomalyTestFixtures.event("2026-06-23T00:00:00Z", 10);
        event.setStatus("denied");
        event.setErrorCode("rate_limit_exceeded");
        event.setRetryAttempt(2);

        StaticResultContext context = new StaticResultContextParser().parse(event);

        assertEquals("DENIED", context.status());
        assertEquals("RATE_LIMIT_EXCEEDED", context.errorCode());
        assertTrue(context.denied());
        assertTrue(context.retried());
    }
}
