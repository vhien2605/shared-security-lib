package vdt.mini.management_service.service.anomaly.rolling;

import vdt.mini.management_service.dto.event.AnomalyGroupKey;
import vdt.mini.management_service.dto.event.RollingWindowEntry;
import vdt.mini.management_service.dto.event.RollingWindowSnapshot;

import java.time.Instant;

public interface RollingWindowStore {
    RollingWindowSnapshot snapshotBefore(AnomalyGroupKey key, Instant currentTimestamp);
    void add(AnomalyGroupKey key, RollingWindowEntry entry);
}
