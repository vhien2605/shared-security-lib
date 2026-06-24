package vdt.mini.management_service.service.anomaly.runtime;

import org.springframework.stereotype.Service;
import vdt.mini.management_service.config.AnomalyDetectionProperties;
import vdt.mini.management_service.dto.event.*;
import vdt.mini.management_service.util.enums.AnomalyDecision;
import vdt.mini.management_service.util.enums.RuleConfidence;

import java.util.List;
import java.util.Locale;

@Service
public class AnomalySeverityResolver {
    private final AnomalyDetectionProperties properties;

    public AnomalySeverityResolver(AnomalyDetectionProperties properties) {
        this.properties = properties;
    }

    public String resolve(AnomalyDecision decision, RiskScoreResult riskScore, RuleConfidence highestConfidence, AnomalyContext context, List<RuleMatch> matches) {
        int risk = riskScore == null ? 0 : riskScore.totalScore();
        int sourceWeight = sourceSeverityWeight(context == null || context.event() == null ? null : context.event().getAlertSeverity());
        boolean hasStrongRule = highestConfidence == RuleConfidence.HIGH && matches != null && !matches.isEmpty();
        boolean windowReady = context != null && context.confidence() != null && context.confidence().windowSampleSufficient();
        long impacted = impactedCount(context == null ? null : context.rollingSnapshot());
        if (decision == AnomalyDecision.ANOMALY) {
            if (risk >= properties.getRisk().getCriticalSeverityThreshold()
                    && hasStrongRule && windowReady && (impacted >= 10 || sourceWeight >= 3)) return "CRITICAL";
            return "HIGH";
        }
        if (decision == AnomalyDecision.SUSPICIOUS) {
            if (risk >= properties.getRisk().getHighSeverityThreshold() && hasStrongRule && sourceWeight >= 2) return "HIGH";
            return "MEDIUM";
        }
        if (decision == AnomalyDecision.OBSERVE) {
            return risk >= properties.getRisk().getMediumSeverityThreshold() && hasStrongRule ? "MEDIUM" : "LOW";
        }
        return "LOW";
    }

    public int sourceSeverityWeight(String severity) {
        if (severity == null || severity.isBlank()) return 0;
        return switch (severity.trim().toUpperCase(Locale.ROOT)) {
            case "MEDIUM", "WARNING" -> 1;
            case "HIGH" -> 2;
            case "CRITICAL" -> 3;
            default -> 0;
        };
    }

    private long impactedCount(RollingWindowSnapshot snapshot) {
        if (snapshot == null) return 0;
        return Math.max(snapshot.failedCountLast5m(), Math.max(snapshot.deniedCountLast5m(), snapshot.retryCountLast5m()));
    }
}
