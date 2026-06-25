package vdt.mini.management_service.service.anomaly.rule;

import org.junit.jupiter.api.Test;
import vdt.mini.management_service.dto.event.*;
import vdt.mini.management_service.service.anomaly.runtime.AnomalyTestFixtures;
import vdt.mini.management_service.util.enums.AnomalyType;
import vdt.mini.management_service.util.enums.RuleConfidence;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CompositeRiskScorerTest {
    private final CompositeRiskScorer scorer = new CompositeRiskScorer();

    @Test
    void score_nullMetrics_shouldNotContribute() {
        AnomalyContext context = new AnomalyContext(AnomalyTestFixtures.event("2026-06-23T00:00:00Z", 100), AnomalyTestFixtures.key(),
                new StaticResultContext("SUCCESS", null, null, null, null, null, null, false, false, false), null, null, null,
                new HistoricalDeviation(null, null, null, null, null, false, false, false),
                new BehaviorDeviation(null, null, null, null, null, null, null, null, null, null, null),
                new BaselineConfidence(false, false, false, false));

        RiskScoreResult result = scorer.score(context, List.of());

        assertThat(result.totalScore()).isZero();
        assertThat(result.contributions()).isEmpty();
    }

    @Test
    void score_sourceSeverityAndStaticContext_contributeButDoNotDecide() {
        var event = AnomalyTestFixtures.event("2026-06-23T00:00:00Z", 100);
        event.setAlertSeverity("WARNING");
        AnomalyContext context = new AnomalyContext(event, AnomalyTestFixtures.key(),
                new StaticResultContext("FAILED", null, "E1", null, null, 1, null, true, false, true), null, null, null,
                null, null, new BaselineConfidence(false, false, false, false));

        RiskScoreResult result = scorer.score(context, List.of());

        assertThat(result.sourceSeverityPoints()).isEqualTo(1);
        assertThat(result.contributions()).containsEntry("failed", 1).containsEntry("retryAttempt", 1).containsEntry("sourceAlertSeverity", 1);
    }

    @Test
    void score_withAnomalyType_shouldOnlyUseRelevantContributions() {
        var event = AnomalyTestFixtures.event("2026-06-23T00:00:00Z", 100);
        event.setAlertSeverity("CRITICAL");
        AnomalyContext context = new AnomalyContext(event, AnomalyTestFixtures.key(),
                new StaticResultContext("FAILED", null, null, null, null, 1, null, true, true, true), null, null, null,
                null, new BehaviorDeviation(null, 4.0, 4.0, 4.0, null, null, null, null, null, null, null),
                new BaselineConfidence(false, true, true, true));
        RuleMatch failureMatch = new RuleMatch("BEHAVIOR_FAILURE_001", AnomalyType.FAILURE_SPIKE, 5, RuleConfidence.HIGH,
                List.of("failureRateLast5m"), "failure spike");

        RiskScoreResult result = scorer.score(context, List.of(failureMatch), AnomalyType.FAILURE_SPIKE);

        assertThat(result.totalScore()).isEqualTo(7);
        assertThat(result.contributions())
                .containsEntry("failureRateRobustZ", 3)
                .containsEntry("failed", 1)
                .containsEntry("sourceAlertSeverity", 3)
                .doesNotContainKeys("deniedRateRobustZ", "denied", "retryRateRobustZ", "retryAttempt");
    }
}
