package vdt.mini.management_service.service.anomaly.rule;

import org.springframework.stereotype.Service;
import vdt.mini.management_service.dto.event.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class CompositeRiskScorer {
    public RiskScoreResult score(AnomalyContext context, List<RuleMatch> existingMatches) {
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
}
