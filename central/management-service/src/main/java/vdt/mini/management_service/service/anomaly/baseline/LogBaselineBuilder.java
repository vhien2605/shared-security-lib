package vdt.mini.management_service.service.anomaly.baseline;

import org.springframework.stereotype.Service;
import vdt.mini.management_service.config.AnomalyDetectionProperties;
import vdt.mini.management_service.dto.event.AnomalyGroupKey;
import vdt.mini.management_service.dto.event.LogBaselineSnapshot;
import vdt.mini.management_service.dto.event.SecurityLogEventMessage;
import vdt.mini.management_service.service.anomaly.stat.IqrCalculator;
import vdt.mini.management_service.service.anomaly.stat.PercentileCalculator;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class LogBaselineBuilder {
    private final PercentileCalculator percentileCalculator;
    private final IqrCalculator iqrCalculator;
    private final BaselineVersionGenerator versionGenerator;
    private final AnomalyDetectionProperties properties;

    public LogBaselineBuilder(PercentileCalculator percentileCalculator,
                              IqrCalculator iqrCalculator,
                              BaselineVersionGenerator versionGenerator,
                              AnomalyDetectionProperties properties) {
        this.percentileCalculator = percentileCalculator;
        this.iqrCalculator = iqrCalculator;
        this.versionGenerator = versionGenerator;
        this.properties = properties;
    }

    public List<LogBaselineSnapshot> build(List<SecurityLogEventMessage> events) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        return events.stream()
                .filter(this::hasGroupKey)
                .collect(Collectors.groupingBy(SecurityLogEventMessage::groupKey))
                .entrySet()
                .stream()
                .map(entry -> buildForGroup(entry.getKey(), entry.getValue()))
                .toList();
    }

    public LogBaselineSnapshot buildForGroup(AnomalyGroupKey key, List<SecurityLogEventMessage> events) {
        List<SecurityLogEventMessage> safeEvents = events == null ? List.of() : events;
        List<Long> durations = safeEvents.stream().map(SecurityLogEventMessage::getDurationMs).filter(Objects::nonNull).toList();
        List<Long> requestSizes = safeEvents.stream().map(SecurityLogEventMessage::getRequestSizeBytes).filter(Objects::nonNull).toList();
        List<Long> responseSizes = safeEvents.stream().map(SecurityLogEventMessage::getResponseSizeBytes).filter(Objects::nonNull).toList();
        List<Long> messageSizes = safeEvents.stream().map(SecurityLogEventMessage::getMessageSizeBytes).filter(Objects::nonNull).toList();
        List<Integer> retries = safeEvents.stream().map(SecurityLogEventMessage::getRetryAttempt).filter(Objects::nonNull).toList();
        return new LogBaselineSnapshot(key, safeEvents.size(),
                percentileCalculator.percentile(durations, 50), percentileCalculator.percentile(durations, 95), percentileCalculator.percentile(durations, 99), iqrCalculator.iqr(durations),
                percentileCalculator.percentile(requestSizes, 50), percentileCalculator.percentile(requestSizes, 95), iqrCalculator.iqr(requestSizes),
                percentileCalculator.percentile(responseSizes, 50), percentileCalculator.percentile(responseSizes, 95), iqrCalculator.iqr(responseSizes),
                percentileCalculator.percentile(messageSizes, 50), percentileCalculator.percentile(messageSizes, 95), iqrCalculator.iqr(messageSizes),
                percentileCalculator.percentile(retries, 50), percentileCalculator.percentile(retries, 95), iqrCalculator.iqr(retries),
                topValues(safeEvents, SecurityLogEventMessage::getClientId), topValues(safeEvents, SecurityLogEventMessage::getSourceIp), topValues(safeEvents, SecurityLogEventMessage::getErrorCode),
                versionGenerator.nextVersion("log-baseline"), Instant.now(), true);
    }

    private boolean hasGroupKey(SecurityLogEventMessage event) {
        return event != null && hasText(event.getServiceId()) && hasText(event.getEndpointId()) && hasText(event.getFlowType());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private List<String> topValues(List<SecurityLogEventMessage> events, Function<SecurityLogEventMessage, String> mapper) {
        int limit = Math.max(1, properties.getBaseline().getKnownValueLimit());
        return events.stream()
                .map(mapper)
                .filter(this::hasText)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()).thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }
}
