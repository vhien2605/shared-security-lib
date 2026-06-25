package vdt.mini.management_service.service.anomaly.runtime;

import vdt.mini.management_service.config.AnomalyDetectionProperties;
import vdt.mini.management_service.dto.event.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class AnomalyTestFixtures {
    private AnomalyTestFixtures() {}

    public static AnomalyDetectionProperties properties() {
        AnomalyDetectionProperties properties = new AnomalyDetectionProperties();
        properties.getBaseline().setMinLogSamples(3);
        properties.getBaseline().setMinBehaviorWindows(2);
        properties.getRolling().setMinSamples(2);
        properties.getRobustZ().setEpsilon(0.0001);
        return properties;
    }

    public static SecurityLogEventMessage event(String timestamp, long durationMs) {
        SecurityLogEventMessage event = new SecurityLogEventMessage();
        event.setTimestamp(timestamp);
        event.setServiceId("svc-1");
        event.setServiceName("svc");
        event.setEndpointId("ep-1");
        event.setEndpointName("ep");
        event.setFlowType("INBOUND_HTTP");
        event.setDirection("INBOUND");
        event.setStatus("SUCCESS");
        event.setDurationMs(durationMs);
        event.setRequestSizeBytes(100L);
        event.setResponseSizeBytes(200L);
        event.setClientId("client-a");
        event.setSourceIp("10.0.0.1");
        return event;
    }

    public static LogBaselineSnapshot logBaseline(long sampleCount) {
        return new LogBaselineSnapshot(key(), sampleCount, 100.0, 150.0, 180.0, 10.0,
                100.0, 120.0, 10.0, 200.0, 220.0, 10.0, null, null, null,
                0.0, 1.0, 1.0, List.of("client-a"), List.of("10.0.0.1"), List.of("E1"), "log-v1", Instant.EPOCH, true);
    }

    public static BehaviorBaselineSnapshot behaviorBaseline(long windowCount) {
        return new BehaviorBaselineSnapshot(key(), windowCount, 10.0, 2.0, 0.01, 0.01,
                0.01, 0.01, 0.01, 0.01, 100.0, 10.0, 100.0, 10.0,
                200.0, 10.0, null, null, 1.0, 1.0, 1.0, 1.0, 0.0, 1.0, "behavior-v1", Instant.EPOCH, true);
    }

    public static AnomalyContext context() {
        return new AnomalyContext(event("2026-06-23T00:00:00Z", 100), key(),
                new StaticResultContext("FAILED", null, "E1", null, null, 1, null, true, false, true),
                logBaseline(3), behaviorBaseline(2), RollingWindowSnapshot.empty(Instant.EPOCH, Instant.EPOCH),
                null, null, new BaselineConfidence(true, true, true, true));
    }

    public static AnomalyGroupKey key() { return new AnomalyGroupKey("svc-1", "ep-1", "INBOUND_HTTP"); }

    public static AnomalyEvent anomalyEvent(String incidentId, String severity) {
        return new AnomalyEvent("anom-1", incidentId, Instant.EPOCH, "LOG_RULE_ENGINE",
                vdt.mini.management_service.util.enums.AnomalyType.FAILURE_SPIKE, severity, "trace-1", "corr-1",
                "svc-1", "svc", "ep-1", "ep", "INBOUND_HTTP", "INBOUND",
                vdt.mini.management_service.util.enums.AnomalyDecision.ANOMALY, 12,
                vdt.mini.management_service.util.enums.RuleConfidence.HIGH, List.of("rule-1"), List.of("feature-1"),
                Map.of(), "rule-set-v1", "log-v1", "behavior-v1", Instant.EPOCH, Instant.EPOCH, 20,
                Instant.EPOCH, Instant.EPOCH, 1, Instant.EPOCH);
    }
}
