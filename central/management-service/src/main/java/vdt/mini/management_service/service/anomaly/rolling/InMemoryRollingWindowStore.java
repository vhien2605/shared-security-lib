package vdt.mini.management_service.service.anomaly.rolling;

import org.springframework.stereotype.Service;
import vdt.mini.management_service.config.AnomalyDetectionProperties;
import vdt.mini.management_service.dto.event.AnomalyGroupKey;
import vdt.mini.management_service.dto.event.RollingWindowEntry;
import vdt.mini.management_service.dto.event.RollingWindowSnapshot;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InMemoryRollingWindowStore implements RollingWindowStore {
    private final Map<AnomalyGroupKey, Deque<RollingWindowEntry>> entries = new ConcurrentHashMap<>();
    private final AnomalyDetectionProperties properties;
    private final RollingWindowSnapshotCalculator snapshotCalculator;

    public InMemoryRollingWindowStore(AnomalyDetectionProperties properties, RollingWindowSnapshotCalculator snapshotCalculator) {
        this.properties = properties;
        this.snapshotCalculator = snapshotCalculator;
    }

    @Override
    public RollingWindowSnapshot snapshotBefore(AnomalyGroupKey key, Instant currentTimestamp) {
        Duration windowSize = properties.getRolling().getWindowSize();
        Instant start = currentTimestamp.minus(windowSize);
        Deque<RollingWindowEntry> deque = entries.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (deque) {
            evict(deque, currentTimestamp);
            return snapshotCalculator.snapshot(new ArrayList<>(deque), start, currentTimestamp);
        }
    }

    @Override
    public void add(AnomalyGroupKey key, RollingWindowEntry entry) {
        Deque<RollingWindowEntry> deque = entries.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (deque) {
            deque.addLast(entry);
            evict(deque, entry.timestamp());
        }
    }

    private void evict(Deque<RollingWindowEntry> deque, Instant currentTimestamp) {
        Instant cutoff = currentTimestamp.minus(properties.getRolling().getWindowSize()).minus(properties.getRolling().getLateTolerance());
        while (!deque.isEmpty() && deque.peekFirst().timestamp().isBefore(cutoff)) {
            deque.removeFirst();
        }
    }
}
