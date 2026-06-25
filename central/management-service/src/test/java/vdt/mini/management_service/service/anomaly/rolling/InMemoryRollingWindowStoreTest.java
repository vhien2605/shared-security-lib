package vdt.mini.management_service.service.anomaly.rolling;

import org.junit.jupiter.api.Test;
import vdt.mini.management_service.dto.event.RollingWindowEntry;
import vdt.mini.management_service.service.anomaly.runtime.AnomalyTestFixtures;
import vdt.mini.management_service.service.anomaly.stat.PercentileCalculator;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InMemoryRollingWindowStoreTest {
    @Test
    void snapshotBefore_shouldEvictOldEntriesAndAddOnlyAfterExplicitAdd() {
        InMemoryRollingWindowStore store = new InMemoryRollingWindowStore(AnomalyTestFixtures.properties(), new RollingWindowSnapshotCalculator(new PercentileCalculator()));
        Instant now = Instant.parse("2026-06-23T00:10:00Z");
        store.add(AnomalyTestFixtures.key(), new RollingWindowEntry(now.minusSeconds(400), "SUCCESS", 1L, null, null, null, null, null, null, null, null));
        store.add(AnomalyTestFixtures.key(), new RollingWindowEntry(now.minusSeconds(10), "SUCCESS", 1L, null, null, null, null, null, null, null, null));

        assertEquals(1, store.snapshotBefore(AnomalyTestFixtures.key(), now).windowSampleCount());
    }
}
