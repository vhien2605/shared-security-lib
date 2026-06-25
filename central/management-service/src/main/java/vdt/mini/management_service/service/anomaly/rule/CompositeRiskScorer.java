package vdt.mini.management_service.service.anomaly.rule;

import org.springframework.stereotype.Service;
import vdt.mini.management_service.dto.event.*;
import vdt.mini.management_service.util.enums.AnomalyType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class CompositeRiskScorer {
    public RiskScoreResult score(AnomalyContext context, List<RuleMatch> existingMatches) {
        return score(context, existingMatches, null);
    }

    public RiskScoreResult score(AnomalyContext context, List<RuleMatch> existingMatches, AnomalyType anomalyType) {
        Map<String, Integer> contributions = new LinkedHashMap<>();
        if (context != null) {
            HistoricalDeviation h = context.historicalDeviation();
            if (h != null) {
                addIfAtLeast(contributions, "durationRobustZ", h.durationRobustZ(), 3, 2);
                addIfAtLeast(contributions, "retryAttemptRobustZ", h.retryAttemptRobustZ(), 3, 2);
                addIfTrue(contributions, "rareClient", h.rareClient(), 1);
                addIfTrue(contributions, "rareSourceIp", h.rareSourceIp(), 1);
                addIfTrue(contributions, "rareErrorCode", h.rareErrorCode(), 2);
            }
            BehaviorDeviation b = context.behaviorDeviation();
            if (b != null) {
                addIfAtLeast(contributions, "failureRateRobustZ", b.failureRateRobustZ(), 3, 3);
                addIfAtLeast(contributions, "deniedRateRobustZ", b.deniedRateRobustZ(), 3, 3);
                addIfAtLeast(contributions, "retryRateRobustZ", b.retryRateRobustZ(), 3, 2);
                addIfAtLeast(contributions, "requestCountRobustZ", b.requestCountRobustZ(), 3, 1);
                addIfAtLeast(contributions, "p95DurationRobustZ", b.p95DurationRobustZ(), 3, 2);
                addIfAtLeast(contributions, "uniqueSourceIpCountRobustZ", b.uniqueSourceIpCountRobustZ(), 3, 2);
                addIfAtLeast(contributions, "uniqueErrorCodeCountRobustZ", b.uniqueErrorCodeCountRobustZ(), 3, 2);
            }
            StaticResultContext s = context.staticContext();
            if (s != null) {
                addIfTrue(contributions, "failed", s.failed(), 1);
                addIfTrue(contributions, "denied", s.denied(), 1);
                addIfTrue(contributions, "rollbackOrCompensation", hasRollbackOrCompensation(s.rollbackStrategy()), 2);
                addIfTrue(contributions, "retryAttempt", s.retryAttempt() != null && s.retryAttempt() > 0 && !contributions.containsKey("retryAttemptRobustZ"), 1);
            }
            int sourceSeverityPoints = sourceSeverityPoints(context.event() == null ? null : context.event().getAlertSeverity());
            if (sourceSeverityPoints > 0) {
                contributions.put("sourceAlertSeverity", sourceSeverityPoints);
            }
            if (anomalyType != null) {
                contributions.keySet().retainAll(relevantContributionKeys(anomalyType));
            }
            int featureScore = contributions.values().stream().mapToInt(Integer::intValue).sum();
            int existingScore = existingMatches == null ? 0 : existingMatches.stream().mapToInt(RuleMatch::riskPoints).sum();
            return new RiskScoreResult(Math.max(existingScore, featureScore), sourceSeverityPoints, Map.copyOf(contributions));
        }
        return new RiskScoreResult(0, 0, Map.of());
    }

    public RiskScoreResult scoreFinal(AnomalyContext context, List<RuleMatch> allMatches, RiskScoreResult initialScore) {
        RiskScoreResult base = score(context, allMatches);
        return new RiskScoreResult(Math.max(base.totalScore(), initialScore == null ? 0 : initialScore.totalScore()), base.sourceSeverityPoints(), base.contributions());
    }

    public int sourceSeverityPoints(String severity) {
        if (severity == null || severity.isBlank()) return 0;
        return switch (severity.trim().toUpperCase(Locale.ROOT)) {
            case "MEDIUM", "WARNING" -> 1;
            case "HIGH" -> 2;
            case "CRITICAL" -> 3;
            default -> 0;
        };
    }

    private void addIfAtLeast(Map<String, Integer> contributions, String key, Double value, double threshold, int points) {
        if (value != null && value >= threshold) contributions.put(key, points);
    }

    private void addIfTrue(Map<String, Integer> contributions, String key, boolean matched, int points) {
        if (matched) contributions.put(key, points);
    }

    private boolean hasRollbackOrCompensation(String rollbackStrategy) {
        if (rollbackStrategy == null) return false;
        String normalized = rollbackStrategy.toUpperCase(Locale.ROOT);
        return normalized.contains("ROLLBACK") || normalized.contains("COMPENSATION");
    }

    private Set<String> relevantContributionKeys(AnomalyType anomalyType) {
        return switch (anomalyType) {
            case LATENCY_OUTLIER, LATENCY_DRIFT -> Set.of("durationRobustZ", "p95DurationRobustZ", "sourceAlertSeverity");
            case REQUEST_SIZE_OUTLIER, REQUEST_SIZE_DRIFT -> Set.of("requestCountRobustZ", "sourceAlertSeverity");
            case RESPONSE_SIZE_OUTLIER, RESPONSE_SIZE_DRIFT -> Set.of("sourceAlertSeverity");
            case MESSAGE_SIZE_OUTLIER, MESSAGE_SIZE_DRIFT -> Set.of("sourceAlertSeverity");
            case RETRY_OUTLIER -> Set.of("retryAttemptRobustZ", "retryAttempt", "sourceAlertSeverity");
            case NEW_OR_RARE_CLIENT -> Set.of("rareClient", "sourceAlertSeverity");
            case NEW_OR_RARE_SOURCE_IP -> Set.of("rareSourceIp", "sourceAlertSeverity");
            case RARE_ERROR_CODE -> Set.of("rareErrorCode", "sourceAlertSeverity");
            case FAILURE_SPIKE -> Set.of("failureRateRobustZ", "failed", "sourceAlertSeverity");
            case DENIED_SPIKE -> Set.of("deniedRateRobustZ", "denied", "sourceAlertSeverity");
            case RETRY_SPIKE -> Set.of("retryRateRobustZ", "retryAttempt", "sourceAlertSeverity");
            case TRAFFIC_SPIKE, TRAFFIC_DROP -> Set.of("requestCountRobustZ", "sourceAlertSeverity");
            case SOURCE_IP_SPIKE -> Set.of("uniqueSourceIpCountRobustZ", "rareSourceIp", "sourceAlertSeverity");
            case CLIENT_DIVERSITY_SPIKE -> Set.of("rareClient", "sourceAlertSeverity");
            case ERROR_DISTRIBUTION_DRIFT -> Set.of("uniqueErrorCodeCountRobustZ", "rareErrorCode", "sourceAlertSeverity");
            case UPSTREAM_DEGRADATION -> Set.of("p95DurationRobustZ", "retryRateRobustZ", "failureRateRobustZ", "failed", "retryAttempt", "sourceAlertSeverity");
            case AUTHENTICATION_ATTACK_PATTERN -> Set.of("deniedRateRobustZ", "uniqueSourceIpCountRobustZ", "denied", "rareSourceIp", "sourceAlertSeverity");
            case DEPENDENCY_INSTABILITY -> Set.of("retryRateRobustZ", "failureRateRobustZ", "uniqueErrorCodeCountRobustZ", "failed", "retryAttempt", "rareErrorCode", "rollbackOrCompensation", "sourceAlertSeverity");
        };
    }
}
