package vdt.mini.management_service.service.anomaly.rolling;

import org.springframework.stereotype.Service;
import vdt.mini.management_service.dto.event.RollingWindowEntry;
import vdt.mini.management_service.dto.event.RollingWindowSnapshot;
import vdt.mini.management_service.service.anomaly.stat.PercentileCalculator;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RollingWindowSnapshotCalculator {
    private final PercentileCalculator percentileCalculator;

    public RollingWindowSnapshotCalculator(PercentileCalculator percentileCalculator) {
        this.percentileCalculator = percentileCalculator;
    }

    public RollingWindowSnapshot snapshot(Collection<RollingWindowEntry> entries, Instant windowStart, Instant windowEnd) {
        List<RollingWindowEntry> valid = entries == null ? List.of() : entries.stream()
                .filter(entry -> entry.timestamp() != null)
                .filter(entry -> !entry.timestamp().isBefore(windowStart) && entry.timestamp().isBefore(windowEnd))
                .sorted(Comparator.comparing(RollingWindowEntry::timestamp))
                .toList();
        if (valid.isEmpty()) {
            return RollingWindowSnapshot.empty(windowStart, windowEnd);
        }
        long failed = valid.stream().filter(this::isFailed).count();
        long denied = valid.stream().filter(entry -> "DENIED".equalsIgnoreCase(entry.status())).count();
        long retried = valid.stream().filter(entry -> entry.retryAttempt() != null && entry.retryAttempt() > 0).count();
        List<Long> durations = valid.stream().map(RollingWindowEntry::durationMs).filter(Objects::nonNull).toList();
        List<Long> requestSizes = valid.stream().map(RollingWindowEntry::requestSizeBytes).filter(Objects::nonNull).toList();
        List<Long> responseSizes = valid.stream().map(RollingWindowEntry::responseSizeBytes).filter(Objects::nonNull).toList();
        List<Long> messageSizes = valid.stream().map(RollingWindowEntry::messageSizeBytes).filter(Objects::nonNull).toList();
        Map<String, Long> errors = valid.stream().map(RollingWindowEntry::errorCode).filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        String dominantError = errors.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        long dominantCount = dominantError == null ? 0 : errors.get(dominantError);
        int count = valid.size();
        return new RollingWindowSnapshot(windowStart, windowEnd, count, count, failed, denied, retried,
                rate(failed, count), rate(denied, count), rate(retried, count), avg(durations), percentileCalculator.percentile(durations, 50),
                percentileCalculator.percentile(durations, 95), durations.stream().mapToDouble(Long::doubleValue).max().stream().boxed().findFirst().orElse(null),
                avg(requestSizes), avg(responseSizes), avg(messageSizes),
                distinct(valid, RollingWindowEntry::clientId), distinct(valid, RollingWindowEntry::sourceIp), distinct(valid, RollingWindowEntry::errorCode),
                dominantError, rate(dominantCount, count), valid.getLast().timestamp());
    }

    private boolean isFailed(RollingWindowEntry entry) {
        return "FAILED".equalsIgnoreCase(entry.status()) || "ERROR".equalsIgnoreCase(entry.status()) || "TIMEOUT".equalsIgnoreCase(entry.status());
    }

    private double rate(long numerator, long denominator) {
        return denominator == 0 ? 0.0 : (double) numerator / denominator;
    }

    private Double avg(List<Long> values) {
        return values.isEmpty() ? null : values.stream().mapToDouble(Long::doubleValue).average().orElse(0.0);
    }

    private int distinct(List<RollingWindowEntry> entries, Function<RollingWindowEntry, String> mapper) {
        return (int) entries.stream().map(mapper).filter(Objects::nonNull).distinct().count();
    }
}
