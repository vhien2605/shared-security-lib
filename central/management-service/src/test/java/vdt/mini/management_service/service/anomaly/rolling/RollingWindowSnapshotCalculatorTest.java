package vdt.mini.management_service.service.anomaly.rolling;

import org.junit.jupiter.api.Test;
import vdt.mini.management_service.dto.event.RollingWindowEntry;
import vdt.mini.management_service.dto.event.RollingWindowSnapshot;
import vdt.mini.management_service.service.anomaly.stat.PercentileCalculator;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RollingWindowSnapshotCalculatorTest {
    @Test
    void snapshot_shouldCalculateRatesAndExcludeCurrentTimestamp() {
        RollingWindowSnapshotCalculator calculator = new RollingWindowSnapshotCalculator(new PercentileCalculator());
        Instant end = Instant.parse("2026-06-23T00:05:00Z");
        RollingWindowEntry previous = new RollingWindowEntry(end.minusSeconds(60), "FAILED", 100L, 10L, 20L, null, 1, "c1", "ip1", "E1", null);
        RollingWindowEntry currentBoundary = new RollingWindowEntry(end, "FAILED", 1000L, null, null, null, null, "c2", "ip2", "E2", null);

        RollingWindowSnapshot snapshot = calculator.snapshot(List.of(previous, currentBoundary), end.minusSeconds(300), end);

        assertEquals(1, snapshot.windowSampleCount());
        assertEquals(1.0, snapshot.failureRateLast5m());
        assertEquals("E1", snapshot.dominantErrorCode());
    }
}
