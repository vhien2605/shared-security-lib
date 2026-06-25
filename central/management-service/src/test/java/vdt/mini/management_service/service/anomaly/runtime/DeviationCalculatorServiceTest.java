package vdt.mini.management_service.service.anomaly.runtime;

import org.junit.jupiter.api.Test;
import vdt.mini.management_service.dto.event.AnomalyContext;
import vdt.mini.management_service.dto.event.RollingWindowSnapshot;
import vdt.mini.management_service.service.anomaly.stat.RobustZCalculator;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class DeviationCalculatorServiceTest {
    @Test
    void calculate_shouldSetConfidenceAndSkipNullNumericDeviation() {
        var properties = AnomalyTestFixtures.properties();
        DeviationCalculatorService service = new DeviationCalculatorService(new RobustZCalculator(properties), properties);
        var event = AnomalyTestFixtures.event("2026-06-23T00:00:00Z", 170);
        event.setRequestSizeBytes(null);
        event.setClientId("new-client");
        var snapshot = new RollingWindowSnapshot(Instant.EPOCH, Instant.EPOCH.plusSeconds(60), 2, 2, 0, 0, 0, 0, 0, 0, null, null, null, null, null, null, null, 0, 0, 0, null, 0, Instant.EPOCH);

        AnomalyContext context = service.calculate(event, AnomalyTestFixtures.key(), new StaticResultContextParser().parse(event), AnomalyTestFixtures.logBaseline(3), AnomalyTestFixtures.behaviorBaseline(2), snapshot);

        assertTrue(context.confidence().hasHistoricalLogBaseline());
        assertTrue(context.historicalDeviation().rareClient());
        assertNull(context.historicalDeviation().requestSizeRobustZ());
        assertEquals(7.0, context.historicalDeviation().durationRobustZ());
    }
}
