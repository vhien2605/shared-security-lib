package vdt.mini.management_service.service.anomaly.baseline;

import org.junit.jupiter.api.Test;
import vdt.mini.management_service.dto.event.LogBaselineSnapshot;
import vdt.mini.management_service.dto.event.SecurityLogEventMessage;
import vdt.mini.management_service.service.anomaly.runtime.AnomalyTestFixtures;
import vdt.mini.management_service.service.anomaly.stat.IqrCalculator;
import vdt.mini.management_service.service.anomaly.stat.PercentileCalculator;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LogBaselineBuilderTest {
    @Test
    void build_shouldGroupByKeyAndNotTreatNullAsZero() {
        LogBaselineBuilder builder = new LogBaselineBuilder(new PercentileCalculator(), new IqrCalculator(new PercentileCalculator()), new BaselineVersionGenerator(), AnomalyTestFixtures.properties());
        SecurityLogEventMessage first = AnomalyTestFixtures.event("2026-06-23T00:00:00Z", 100);
        SecurityLogEventMessage second = AnomalyTestFixtures.event("2026-06-23T00:01:00Z", 200);
        second.setRequestSizeBytes(null);

        LogBaselineSnapshot snapshot = builder.build(List.of(first, second)).getFirst();

        assertEquals(2, snapshot.sampleCount());
        assertEquals(150.0, snapshot.medianDurationMs());
        assertEquals(100.0, snapshot.medianRequestSizeBytes());
        assertTrue(snapshot.baselineVersion().startsWith("log-baseline-"));
    }
}
