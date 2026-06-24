package vdt.mini.management_service.service.anomaly.rule;

import org.junit.jupiter.api.Test;
import vdt.mini.management_service.dto.event.*;
import vdt.mini.management_service.service.anomaly.runtime.AnomalyTestFixtures;
import vdt.mini.management_service.util.enums.AnomalyDecision;
import vdt.mini.management_service.util.enums.AnomalyType;

import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CompositeRiskEvaluatorTest {
    private final CompositeRiskEvaluator evaluator = new CompositeRiskEvaluator();

    @Test
    void evaluate_upstreamSignals_shouldMatchComposite() {
        RuleEvaluationResult result = evaluator.evaluate(context(3.5, 3.5, 3.5, null, 0, false), new RiskScoreResult(7, 0, java.util.Map.of()), List.of());

        assertThat(result.decision()).isEqualTo(AnomalyDecision.ANOMALY);
        assertThat(result.matches()).extracting(RuleMatch::anomalyType).contains(AnomalyType.UPSTREAM_DEGRADATION);
    }

    @Test
    void evaluate_staticContextAlone_shouldNotMatch() {
        RuleEvaluationResult result = evaluator.evaluate(context(null, null, null, null, 1, true), new RiskScoreResult(2, 0, java.util.Map.of()), List.of());

        assertThat(result.matches()).isEmpty();
    }

    private AnomalyContext context(Double latencyZ, Double retryZ, Double failureZ, Double deniedZ, int retryAttempt, boolean failed) {
        var snapshot = new RollingWindowSnapshot(Instant.EPOCH, Instant.EPOCH.plusSeconds(60), 20, 20, 10, 0, 10, 0.5, 0, 0.5,
                null, null, 200.0, null, null, null, null, 0, 3, 3, null, 0, Instant.EPOCH);
        return new AnomalyContext(AnomalyTestFixtures.event("2026-06-23T00:00:00Z", 100), AnomalyTestFixtures.key(),
                new StaticResultContext(failed ? "FAILED" : "SUCCESS", null, failed ? "E1" : null, null, null, retryAttempt, null, failed, false, retryAttempt > 0),
                null, AnomalyTestFixtures.behaviorBaseline(2), snapshot, null,
                new BehaviorDeviation(null, failureZ, deniedZ, retryZ, latencyZ, null, null, null, null, null, null),
                new BaselineConfidence(false, true, true, true));
    }
}
