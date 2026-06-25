package vdt.mini.management_service.service.anomaly.rule;

import org.springframework.stereotype.Service;
import vdt.mini.management_service.dto.event.*;
import vdt.mini.management_service.util.enums.AnomalyDecision;
import vdt.mini.management_service.util.enums.AnomalyType;
import vdt.mini.management_service.util.enums.RuleConfidence;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class CompositeRiskEvaluator {
    public RuleEvaluationResult evaluate(AnomalyContext context, RiskScoreResult riskScore, List<RuleMatch> existingMatches) {
        if (context == null || context.behaviorDeviation() == null || context.rollingSnapshot() == null || context.confidence() == null
                || !context.confidence().hasHistoricalBehaviorBaseline() || !context.confidence().windowSampleSufficient()) {
            return RuleEvaluationResult.normal();
        }
        List<RuleMatch> matches = new ArrayList<>();
        if (hasUpstreamDegradation(context, existingMatches)) {
            matches.add(new RuleMatch("COMPOSITE_UPSTREAM_001", AnomalyType.UPSTREAM_DEGRADATION, 4, RuleConfidence.HIGH,
                    List.of("p95DurationRobustZ", "retryRateRobustZ", "failureRateRobustZ"), "Latency, retry and failure signals increased together"));
        }
        if (hasAuthenticationAttack(context, existingMatches)) {
            matches.add(new RuleMatch("COMPOSITE_AUTH_ATTACK_001", AnomalyType.AUTHENTICATION_ATTACK_PATTERN, 4, RuleConfidence.HIGH,
                    List.of("deniedRateRobustZ", "uniqueSourceIpCountRobustZ", "denyReason"), "Denied and source IP diversity signals indicate authentication attack pattern"));
        }
        if (hasDependencyInstability(context, existingMatches)) {
            matches.add(new RuleMatch("COMPOSITE_DEPENDENCY_001", AnomalyType.DEPENDENCY_INSTABILITY, 4, RuleConfidence.HIGH,
                    List.of("retryRateRobustZ", "failureRateRobustZ", "uniqueErrorCodeCountRobustZ", "retryAttempt"), "Retry, failure and error diversity signals indicate dependency instability"));
        }
        if (matches.isEmpty()) return RuleEvaluationResult.normal();
        return new RuleEvaluationResult(matches.stream().anyMatch(m -> m.confidence() == RuleConfidence.HIGH) ? AnomalyDecision.ANOMALY : AnomalyDecision.SUSPICIOUS, matches);
    }

    private boolean hasUpstreamDegradation(AnomalyContext context, List<RuleMatch> existingMatches) {
        BehaviorDeviation d = context.behaviorDeviation();
        return signal(d.p95DurationRobustZ(), AnomalyType.LATENCY_DRIFT, existingMatches)
                && signal(d.retryRateRobustZ(), AnomalyType.RETRY_SPIKE, existingMatches)
                && signal(d.failureRateRobustZ(), AnomalyType.FAILURE_SPIKE, existingMatches);
    }

    private boolean hasAuthenticationAttack(AnomalyContext context, List<RuleMatch> existingMatches) {
        BehaviorDeviation d = context.behaviorDeviation();
        StaticResultContext s = context.staticContext();
        boolean authContext = s != null && (s.denied() || containsAuthOrAccess(s.denyReason()) || "DENIED".equalsIgnoreCase(s.status()));
        return authContext && signal(d.deniedRateRobustZ(), AnomalyType.DENIED_SPIKE, existingMatches)
                && signal(d.uniqueSourceIpCountRobustZ(), AnomalyType.SOURCE_IP_SPIKE, existingMatches);
    }

    private boolean hasDependencyInstability(AnomalyContext context, List<RuleMatch> existingMatches) {
        BehaviorDeviation d = context.behaviorDeviation();
        StaticResultContext s = context.staticContext();
        boolean staticContext = s != null && (s.retried() || s.failed() || hasText(s.errorCode()) || (s.retryAttempt() != null && s.retryAttempt() > 0));
        boolean failureOrErrorDiversity = signal(d.failureRateRobustZ(), AnomalyType.FAILURE_SPIKE, existingMatches)
                || signal(d.uniqueErrorCodeCountRobustZ(), AnomalyType.ERROR_DISTRIBUTION_DRIFT, existingMatches);
        return staticContext && signal(d.retryRateRobustZ(), AnomalyType.RETRY_SPIKE, existingMatches) && failureOrErrorDiversity;
    }

    private boolean signal(Double robustZ, AnomalyType existingType, List<RuleMatch> existingMatches) {
        return (robustZ != null && robustZ >= 3) || (existingMatches != null && existingMatches.stream().anyMatch(m -> m.anomalyType() == existingType));
    }

    private boolean containsAuthOrAccess(String value) {
        if (value == null) return false;
        String normalized = value.toUpperCase(Locale.ROOT);
        return normalized.contains("AUTH") || normalized.contains("ACCESS");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
