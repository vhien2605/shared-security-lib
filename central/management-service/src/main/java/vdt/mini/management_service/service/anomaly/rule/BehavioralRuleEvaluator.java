package vdt.mini.management_service.service.anomaly.rule;

import org.springframework.stereotype.Service;
import vdt.mini.management_service.dto.event.*;
import vdt.mini.management_service.util.enums.AnomalyDecision;
import vdt.mini.management_service.util.enums.AnomalyType;
import vdt.mini.management_service.util.enums.RuleConfidence;

import java.util.ArrayList;
import java.util.List;

@Service
public class BehavioralRuleEvaluator implements RuleEvaluator {
    private static final double RATE_ROBUST_Z_THRESHOLD = 5.0;
    private static final double MIN_RATE_DELTA = 0.30;
    private static final double MIN_FAILURE_RATE = 0.30;
    private static final double MIN_DENIED_RATE = 0.30;
    private static final double MIN_RETRY_RATE = 0.30;
    private static final long MIN_RATE_EVENT_COUNT = 10;
    private static final int MIN_GENERAL_EVENT_COUNT = 4;

    @Override
    public RuleEvaluationResult evaluate(AnomalyContext context) {
        if (context == null || context.behaviorDeviation() == null || context.rollingSnapshot() == null || context.confidence() == null
                || !context.confidence().hasHistoricalBehaviorBaseline() || !context.confidence().windowSampleSufficient()) {
            return RuleEvaluationResult.normal();
        }
        BehaviorDeviation d = context.behaviorDeviation();
        RollingWindowSnapshot s = context.rollingSnapshot();
        List<RuleMatch> matches = new ArrayList<>();
        BehaviorBaselineSnapshot baseline = context.behaviorBaseline();
        if (rateSpike(s.failureRateLast5m(), baseline.medianFailureRateLast5m(), d.failureRateRobustZ(), MIN_FAILURE_RATE, s.failedCountLast5m())) add(matches, "BEHAVIOR_FAILURE_001", AnomalyType.FAILURE_SPIKE, "failureRateLast5m");
        if (rateSpike(s.deniedRateLast5m(), baseline.medianDeniedRateLast5m(), d.deniedRateRobustZ(), MIN_DENIED_RATE, s.deniedCountLast5m())) add(matches, "BEHAVIOR_DENIED_001", AnomalyType.DENIED_SPIKE, "deniedRateLast5m");
        if (rateSpike(s.retryRateLast5m(), baseline.medianRetryRateLast5m(), d.retryRateRobustZ(), MIN_RETRY_RATE, s.retryCountLast5m())) add(matches, "BEHAVIOR_RETRY_001", AnomalyType.RETRY_SPIKE, "retryRateLast5m");
        if (gte(d.requestCountRobustZ(), 6) && s.windowSampleCount() >= MIN_GENERAL_EVENT_COUNT) add(matches, "BEHAVIOR_TRAFFIC_SPIKE_001", AnomalyType.TRAFFIC_SPIKE, "requestCountRobustZ");
        if (lte(d.requestCountRobustZ(), -5) && context.behaviorBaseline().medianRequestCountLast5m() != null && context.behaviorBaseline().medianRequestCountLast5m() > 0 && s.windowSampleCount() >= MIN_GENERAL_EVENT_COUNT) add(matches, "BEHAVIOR_TRAFFIC_DROP_001", AnomalyType.TRAFFIC_DROP, "requestCountRobustZ");
        if (gte(d.p95DurationRobustZ(), 5) && s.windowSampleCount() >= MIN_GENERAL_EVENT_COUNT) add(matches, "BEHAVIOR_LATENCY_001", AnomalyType.LATENCY_DRIFT, "p95DurationRobustZ");
        if (gte(d.avgRequestSizeRobustZ(), 5) && s.windowSampleCount() >= MIN_GENERAL_EVENT_COUNT) add(matches, "BEHAVIOR_REQ_SIZE_001", AnomalyType.REQUEST_SIZE_DRIFT, "avgRequestSizeRobustZ");
        if (gte(d.avgResponseSizeRobustZ(), 5) && s.windowSampleCount() >= MIN_GENERAL_EVENT_COUNT) add(matches, "BEHAVIOR_RES_SIZE_001", AnomalyType.RESPONSE_SIZE_DRIFT, "avgResponseSizeRobustZ");
        if (gte(d.avgMessageSizeRobustZ(), 5) && s.windowSampleCount() >= MIN_GENERAL_EVENT_COUNT) add(matches, "BEHAVIOR_MSG_SIZE_001", AnomalyType.MESSAGE_SIZE_DRIFT, "avgMessageSizeRobustZ");
        if (gte(d.uniqueSourceIpCountRobustZ(), 5) && s.windowSampleCount() >= MIN_GENERAL_EVENT_COUNT) add(matches, "BEHAVIOR_SOURCE_IP_001", AnomalyType.SOURCE_IP_SPIKE, "uniqueSourceIpCountRobustZ");
        if (gte(d.uniqueClientCountRobustZ(), 5) && s.windowSampleCount() >= MIN_GENERAL_EVENT_COUNT) add(matches, "BEHAVIOR_CLIENT_001", AnomalyType.CLIENT_DIVERSITY_SPIKE, "uniqueClientCountRobustZ");
        if (gte(d.uniqueErrorCodeCountRobustZ(), 5) && s.windowSampleCount() >= MIN_GENERAL_EVENT_COUNT) add(matches, "BEHAVIOR_ERROR_001", AnomalyType.ERROR_DISTRIBUTION_DRIFT, "uniqueErrorCodeCountRobustZ");
        return matches.isEmpty() ? RuleEvaluationResult.normal() : new RuleEvaluationResult(AnomalyDecision.ANOMALY, matches);
    }

    private boolean gte(Double value, double threshold) { return value != null && value >= threshold; }
    private boolean lte(Double value, double threshold) { return value != null && value <= threshold; }
    private boolean rateSpike(double currentRate, Double baselineMedian, Double robustZ, double minimumRate, long count) {
        return currentRate >= minimumRate
                && count >= MIN_RATE_EVENT_COUNT
                && (gte(robustZ, RATE_ROBUST_Z_THRESHOLD) || exceedsBaselineRate(currentRate, baselineMedian));
    }

    private boolean exceedsBaselineRate(double currentRate, Double baselineMedian) {
        return baselineMedian != null && currentRate >= baselineMedian + MIN_RATE_DELTA;
    }

    private void add(List<RuleMatch> matches, String ruleId, AnomalyType type, String feature) {
        matches.add(new RuleMatch(ruleId, type, 5, RuleConfidence.HIGH, List.of(feature), type + " matched"));
    }
}
