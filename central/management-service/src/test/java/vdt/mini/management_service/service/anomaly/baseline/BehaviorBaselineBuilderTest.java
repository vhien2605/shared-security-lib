package vdt.mini.management_service.service.anomaly.baseline;

import org.junit.jupiter.api.Test;
import vdt.mini.management_service.config.AnomalyDetectionProperties;
import vdt.mini.management_service.dto.event.BehaviorBaselineSnapshot;
import vdt.mini.management_service.dto.event.SecurityLogEventMessage;
import vdt.mini.management_service.service.anomaly.rolling.RollingWindowSnapshotCalculator;
import vdt.mini.management_service.service.anomaly.runtime.AnomalyTestFixtures;
import vdt.mini.management_service.service.anomaly.stat.IqrCalculator;
import vdt.mini.management_service.service.anomaly.stat.PercentileCalculator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BehaviorBaselineBuilderTest {
    @Test
    void build_shouldReplaySnapshotBeforeCurrentLog() {
        AnomalyDetectionProperties properties = AnomalyTestFixtures.properties();
        properties.getRolling().setMinSamples(1);
        BehaviorBaselineBuilder builder = new BehaviorBaselineBuilder(new RollingWindowSnapshotCalculator(new PercentileCalculator()), new PercentileCalculator(), new IqrCalculator(new PercentileCalculator()), new BaselineVersionGenerator(), properties);
        List<SecurityLogEventMessage> events = new ArrayList<>();
        Instant start = Instant.parse("2026-06-23T00:00:00Z");
        events.add(AnomalyTestFixtures.event(start.toString(), 100));
        events.add(AnomalyTestFixtures.event(start.plusSeconds(60).toString(), 500));

        BehaviorBaselineSnapshot snapshot = builder.build(events).getFirst();

        assertEquals(1, snapshot.windowCount());
        assertEquals(1.0, snapshot.medianRequestCountLast5m());
        assertEquals(100.0, snapshot.medianP95DurationLast5m());
    }
}
