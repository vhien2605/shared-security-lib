package vdt.mini.management_service.service.anomaly.baseline;

import org.springframework.stereotype.Service;
import vdt.mini.management_service.config.AnomalyDetectionProperties;
import vdt.mini.management_service.dto.event.AnomalyGroupKey;
import vdt.mini.management_service.dto.event.BehaviorBaselineSnapshot;
import vdt.mini.management_service.dto.event.RollingWindowEntry;
import vdt.mini.management_service.dto.event.RollingWindowSnapshot;
import vdt.mini.management_service.dto.event.SecurityLogEventMessage;
import vdt.mini.management_service.service.anomaly.rolling.RollingWindowSnapshotCalculator;
import vdt.mini.management_service.service.anomaly.stat.IqrCalculator;
import vdt.mini.management_service.service.anomaly.stat.PercentileCalculator;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class BehaviorBaselineBuilder {
    private final RollingWindowSnapshotCalculator snapshotCalculator;
    private final PercentileCalculator percentileCalculator;
    private final IqrCalculator iqrCalculator;
    private final BaselineVersionGenerator versionGenerator;
    private final AnomalyDetectionProperties properties;

    public BehaviorBaselineBuilder(RollingWindowSnapshotCalculator snapshotCalculator,
                                   PercentileCalculator percentileCalculator,
                                   IqrCalculator iqrCalculator,
                                   BaselineVersionGenerator versionGenerator,
                                   AnomalyDetectionProperties properties) {
        this.snapshotCalculator = snapshotCalculator;
        this.percentileCalculator = percentileCalculator;
        this.iqrCalculator = iqrCalculator;
        this.versionGenerator = versionGenerator;
        this.properties = properties;
    }

    public List<BehaviorBaselineSnapshot> build(List<SecurityLogEventMessage> events) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        return events.stream()
                .filter(this::validForReplay)
                .collect(Collectors.groupingBy(SecurityLogEventMessage::groupKey))
                .entrySet().stream()
                .map(entry -> buildForGroup(entry.getKey(), entry.getValue()))
                .filter(Objects::nonNull)
                .toList();
    }

    public BehaviorBaselineSnapshot buildForGroup(AnomalyGroupKey key, List<SecurityLogEventMessage> events) {
        List<SecurityLogEventMessage> sorted = events == null ? List.of() : events.stream()
                .filter(this::validForReplay)
                .sorted((left, right) -> Instant.parse(left.getTimestamp()).compareTo(Instant.parse(right.getTimestamp())))
                .toList();
        Duration windowSize = properties.getRolling().getWindowSize();
        Duration sampleInterval = properties.getBaseline().getBehaviorSampleInterval();
        int minSamples = properties.getRolling().getMinSamples();
        ArrayDeque<RollingWindowEntry> window = new ArrayDeque<>();
        java.util.ArrayList<RollingWindowSnapshot> snapshots = new java.util.ArrayList<>();
        Instant nextSampleAt = null;
        Instant lastEventTime = null;
        for (SecurityLogEventMessage event : sorted) {
            Instant current = Instant.parse(event.getTimestamp());
            if (nextSampleAt == null) {
                nextSampleAt = current;
            }
            while (!nextSampleAt.isAfter(current)) {
                addSnapshotIfSufficient(window, nextSampleAt, windowSize, minSamples, snapshots);
                nextSampleAt = nextSampleAt.plus(sampleInterval);
            }
            evict(window, current.minus(windowSize));
            window.addLast(RollingWindowEntry.from(event));
            lastEventTime = current;
        }
        if (lastEventTime != null && nextSampleAt != null && !window.isEmpty()) {
            addSnapshotIfSufficient(window, nextSampleAt, windowSize, minSamples, snapshots);
        }
        return newSnapshot(key, snapshots);
    }

    private void addSnapshotIfSufficient(ArrayDeque<RollingWindowEntry> window, Instant sampleAt, Duration windowSize, int minSamples,
                                         java.util.ArrayList<RollingWindowSnapshot> snapshots) {
        Instant windowStart = sampleAt.minus(windowSize);
        evict(window, windowStart);
        RollingWindowSnapshot snapshot = snapshotCalculator.snapshot(window, windowStart, sampleAt);
        if (snapshot.windowSampleCount() >= minSamples) {
            snapshots.add(snapshot);
        }
    }

    private BehaviorBaselineSnapshot newSnapshot(AnomalyGroupKey key, List<RollingWindowSnapshot> snapshots) {
        return new BehaviorBaselineSnapshot(key, snapshots.size(),
                pct(snapshots.stream().map(RollingWindowSnapshot::requestCountLast5m).toList(), 50), iqr(snapshots.stream().map(RollingWindowSnapshot::requestCountLast5m).toList()),
                pct(snapshots.stream().map(RollingWindowSnapshot::failureRateLast5m).toList(), 50), iqr(snapshots.stream().map(RollingWindowSnapshot::failureRateLast5m).toList()),
                pct(snapshots.stream().map(RollingWindowSnapshot::deniedRateLast5m).toList(), 50), iqr(snapshots.stream().map(RollingWindowSnapshot::deniedRateLast5m).toList()),
                pct(snapshots.stream().map(RollingWindowSnapshot::retryRateLast5m).toList(), 50), iqr(snapshots.stream().map(RollingWindowSnapshot::retryRateLast5m).toList()),
                pct(snapshots.stream().map(RollingWindowSnapshot::p95DurationLast5m).toList(), 50), iqr(snapshots.stream().map(RollingWindowSnapshot::p95DurationLast5m).toList()),
                pct(snapshots.stream().map(RollingWindowSnapshot::avgRequestSizeLast5m).toList(), 50), iqr(snapshots.stream().map(RollingWindowSnapshot::avgRequestSizeLast5m).toList()),
                pct(snapshots.stream().map(RollingWindowSnapshot::avgResponseSizeLast5m).toList(), 50), iqr(snapshots.stream().map(RollingWindowSnapshot::avgResponseSizeLast5m).toList()),
                pct(snapshots.stream().map(RollingWindowSnapshot::avgMessageSizeLast5m).toList(), 50), iqr(snapshots.stream().map(RollingWindowSnapshot::avgMessageSizeLast5m).toList()),
                pct(snapshots.stream().map(RollingWindowSnapshot::uniqueClientCountLast5m).toList(), 50), iqr(snapshots.stream().map(RollingWindowSnapshot::uniqueClientCountLast5m).toList()),
                pct(snapshots.stream().map(RollingWindowSnapshot::uniqueSourceIpCountLast5m).toList(), 50), iqr(snapshots.stream().map(RollingWindowSnapshot::uniqueSourceIpCountLast5m).toList()),
                pct(snapshots.stream().map(RollingWindowSnapshot::uniqueErrorCodeCountLast5m).toList(), 50), iqr(snapshots.stream().map(RollingWindowSnapshot::uniqueErrorCodeCountLast5m).toList()),
                versionGenerator.nextVersion("behavior-baseline"), Instant.now(), true);
    }

    private Double pct(List<? extends Number> values, double percentile) {
        return percentileCalculator.percentile(values, percentile);
    }

    private Double iqr(List<? extends Number> values) {
        return iqrCalculator.iqr(values);
    }

    private void evict(ArrayDeque<RollingWindowEntry> window, Instant cutoff) {
        while (!window.isEmpty() && window.peekFirst().timestamp().isBefore(cutoff)) {
            window.removeFirst();
        }
    }

    private boolean validForReplay(SecurityLogEventMessage event) {
        if (event == null || event.getTimestamp() == null || event.getServiceId() == null || event.getEndpointId() == null || event.getFlowType() == null) {
            return false;
        }
        try {
            Instant.parse(event.getTimestamp());
            return true;
        } catch (DateTimeParseException exception) {
            return false;
        }
    }
}
