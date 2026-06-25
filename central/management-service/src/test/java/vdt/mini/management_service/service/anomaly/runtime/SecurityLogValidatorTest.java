package vdt.mini.management_service.service.anomaly.runtime;

import org.junit.jupiter.api.Test;
import vdt.mini.management_service.dto.event.SecurityLogEventMessage;

import static org.junit.jupiter.api.Assertions.*;

class SecurityLogValidatorTest {
    private final SecurityLogValidator validator = new SecurityLogValidator();

    @Test
    void isValid_shouldRequireTimestampGroupKeyAndKnownFlowType() {
        assertTrue(validator.isValid(AnomalyTestFixtures.event("2026-06-23T00:00:00Z", 10)));
        SecurityLogEventMessage invalidFlow = AnomalyTestFixtures.event("2026-06-23T00:00:00Z", 10);
        invalidFlow.setFlowType("OUTBOUND_MQ_PUBLISHER");
        assertFalse(validator.isValid(invalidFlow));
        SecurityLogEventMessage invalidTimestamp = AnomalyTestFixtures.event("bad", 10);
        assertFalse(validator.isValid(invalidTimestamp));
    }
}
