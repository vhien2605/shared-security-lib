package vdt.mini.management_service.service.anomaly.runtime;

import org.springframework.stereotype.Service;
import vdt.mini.management_service.config.AnomalyDetectionProperties;
import vdt.mini.management_service.dto.event.*;
import vdt.mini.management_service.service.anomaly.stat.RobustZCalculator;

import java.util.List;

@Service
public class DeviationCalculatorService {
    private final RobustZCalculator robustZCalculator;
    private final AnomalyDetectionProperties properties;

    public DeviationCalculatorService(RobustZCalculator robustZCalculator, AnomalyDetectionProperties properties) {
        this.robustZCalculator = robustZCalculator;
        this.properties = properties;
    }

    public AnomalyContext calculate(SecurityLogEventMessage event,
                                    AnomalyGroupKey key,
                                    StaticResultContext staticContext,
                                    LogBaselineSnapshot logBaseline,
                                    BehaviorBaselineSnapshot behaviorBaseline,
                                    RollingWindowSnapshot rollingSnapshot) {
        HistoricalDeviation historical = historical(event, logBaseline);
        BehaviorDeviation behavior = behavior(rollingSnapshot, behaviorBaseline);
        BaselineConfidence confidence = new BaselineConfidence(
                logBaseline != null && logBaseline.sampleCount() >= properties.getBaseline().getMinLogSamples(),
                behaviorBaseline != null && behaviorBaseline.windowCount() >= properties.getBaseline().getMinBehaviorWindows(),
                rollingSnapshot != null && rollingSnapshot.windowSampleCount() > 0,
                rollingSnapshot != null && rollingSnapshot.windowSampleCount() >= properties.getRolling().getMinSamples());
        return new AnomalyContext(event, key, staticContext, logBaseline, behaviorBaseline, rollingSnapshot, historical, behavior, confidence);
    }

    private HistoricalDeviation historical(SecurityLogEventMessage event, LogBaselineSnapshot baseline) {
        if (baseline == null) return new HistoricalDeviation(null, null, null, null, null, false, false, false);
        List<String> clients = baseline.knownClients() == null ? List.of() : baseline.knownClients();
        List<String> sourceIps = baseline.knownSourceIps() == null ? List.of() : baseline.knownSourceIps();
        List<String> errorCodes = baseline.knownErrorCodes() == null ? List.of() : baseline.knownErrorCodes();
        return new HistoricalDeviation(
                robustZCalculator.robustZ(event.getDurationMs(), baseline.medianDurationMs(), baseline.durationIqr()),
                robustZCalculator.robustZ(event.getRequestSizeBytes(), baseline.medianRequestSizeBytes(), baseline.requestSizeIqr()),
                robustZCalculator.robustZ(event.getResponseSizeBytes(), baseline.medianResponseSizeBytes(), baseline.responseSizeIqr()),
                robustZCalculator.robustZ(event.getMessageSizeBytes(), baseline.medianMessageSizeBytes(), baseline.messageSizeIqr()),
                robustZCalculator.robustZ(event.getRetryAttempt(), baseline.medianRetryAttempt(), baseline.retryAttemptIqr()),
                hasText(event.getClientId()) && !clients.contains(event.getClientId()),
                hasText(event.getSourceIp()) && !sourceIps.contains(event.getSourceIp()),
                hasText(event.getErrorCode()) && !errorCodes.contains(event.getErrorCode()));
    }

    private BehaviorDeviation behavior(RollingWindowSnapshot snapshot, BehaviorBaselineSnapshot baseline) {
        if (baseline == null || snapshot == null) return new BehaviorDeviation(null, null, null, null, null, null, null, null, null, null, null);
        return new BehaviorDeviation(
                robustZCalculator.robustZ(snapshot.requestCountLast5m(), baseline.medianRequestCountLast5m(), baseline.requestCountLast5mIqr()),
                robustZCalculator.robustZ(snapshot.failureRateLast5m(), baseline.medianFailureRateLast5m(), baseline.failureRateLast5mIqr()),
                robustZCalculator.robustZ(snapshot.deniedRateLast5m(), baseline.medianDeniedRateLast5m(), baseline.deniedRateLast5mIqr()),
                robustZCalculator.robustZ(snapshot.retryRateLast5m(), baseline.medianRetryRateLast5m(), baseline.retryRateLast5mIqr()),
                robustZCalculator.robustZ(snapshot.p95DurationLast5m(), baseline.medianP95DurationLast5m(), baseline.p95DurationLast5mIqr()),
                robustZCalculator.robustZ(snapshot.avgRequestSizeLast5m(), baseline.medianAvgRequestSizeLast5m(), baseline.avgRequestSizeLast5mIqr()),
                robustZCalculator.robustZ(snapshot.avgResponseSizeLast5m(), baseline.medianAvgResponseSizeLast5m(), baseline.avgResponseSizeLast5mIqr()),
                robustZCalculator.robustZ(snapshot.avgMessageSizeLast5m(), baseline.medianAvgMessageSizeLast5m(), baseline.avgMessageSizeLast5mIqr()),
                robustZCalculator.robustZ(snapshot.uniqueClientCountLast5m(), baseline.medianUniqueClientCountLast5m(), baseline.uniqueClientCountLast5mIqr()),
                robustZCalculator.robustZ(snapshot.uniqueSourceIpCountLast5m(), baseline.medianUniqueSourceIpCountLast5m(), baseline.uniqueSourceIpCountLast5mIqr()),
                robustZCalculator.robustZ(snapshot.uniqueErrorCodeCountLast5m(), baseline.medianUniqueErrorCodeCountLast5m(), baseline.uniqueErrorCodeCountLast5mIqr()));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
