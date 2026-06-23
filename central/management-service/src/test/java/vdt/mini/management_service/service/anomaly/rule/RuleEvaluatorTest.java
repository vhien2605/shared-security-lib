package vdt.mini.management_service.service.anomaly.rule;

import org.junit.jupiter.api.Test;
import vdt.mini.management_service.dto.event.*;
import vdt.mini.management_service.service.anomaly.runtime.AnomalyTestFixtures;
import vdt.mini.management_service.util.enums.AnomalyDecision;
import vdt.mini.management_service.util.enums.AnomalyType;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class RuleEvaluatorTest {
    @Test
    void historicalEvaluator_shouldDetectOutliersAndRareEntityObserve() {
        AnomalyContext outlier = new AnomalyContext(AnomalyTestFixtures.event("2026-06-23T00:00:00Z", 10), AnomalyTestFixtures.key(), null, null, null, null,
                new HistoricalDeviation(6.0, null, null, null, null, false, false, false), null, new BaselineConfidence(true, false, false, false));
        assertEquals(AnomalyDecision.ANOMALY, new HistoricalLogRuleEvaluator().evaluate(outlier).decision());

        AnomalyContext rare = new AnomalyContext(AnomalyTestFixtures.event("2026-06-23T00:00:00Z", 10), AnomalyTestFixtures.key(), null, null, null, null,
                new HistoricalDeviation(null, null, null, null, null, true, false, false), null, new BaselineConfidence(true, false, false, false));
        var result = new HistoricalLogRuleEvaluator().evaluate(rare);
        assertEquals(AnomalyDecision.OBSERVE, result.decision());
        assertEquals(AnomalyType.NEW_OR_RARE_CLIENT, result.matches().getFirst().anomalyType());
    }

    @Test
    void behavioralEvaluator_shouldDetectFailureSpikeOnlyWithSufficientBaselineAndWindow() {
        RollingWindowSnapshot snapshot = new RollingWindowSnapshot(Instant.EPOCH, Instant.EPOCH.plusSeconds(60), 20, 20, 10, 0, 0, 0.5, 0, 0, null, null, null, null, null, null, null, 0, 0, 0, null, 0, Instant.EPOCH);
        AnomalyContext context = new AnomalyContext(AnomalyTestFixtures.event("2026-06-23T00:00:00Z", 10), AnomalyTestFixtures.key(), null, null, AnomalyTestFixtures.behaviorBaseline(2), snapshot,
                null, new BehaviorDeviation(null, 49.0, null, null, null, null, null, null, null, null, null), new BaselineConfidence(false, true, true, true));

        var result = new BehavioralRuleEvaluator().evaluate(context);

        assertEquals(AnomalyDecision.ANOMALY, result.decision());
        assertEquals(AnomalyType.FAILURE_SPIKE, result.matches().getFirst().anomalyType());
    }

    @Test
    void behavioralEvaluator_shouldDetectFailureSpikeWhenRateDeltaIsHighButRobustZIsLow() {
        RollingWindowSnapshot snapshot = new RollingWindowSnapshot(Instant.EPOCH, Instant.EPOCH.plusSeconds(60), 20, 20, 10, 0, 0, 0.5, 0, 0, null, null, null, null, null, null, null, 0, 0, 0, null, 0, Instant.EPOCH);
        AnomalyContext context = new AnomalyContext(AnomalyTestFixtures.event("2026-06-23T00:00:00Z", 10), AnomalyTestFixtures.key(), null, null, AnomalyTestFixtures.behaviorBaseline(2), snapshot,
                null, new BehaviorDeviation(null, 0.49, null, null, null, null, null, null, null, null, null), new BaselineConfidence(false, true, true, true));

        var result = new BehavioralRuleEvaluator().evaluate(context);

        assertEquals(AnomalyDecision.ANOMALY, result.decision());
        assertEquals(AnomalyType.FAILURE_SPIKE, result.matches().getFirst().anomalyType());
    }

    @Test
    void behavioralEvaluator_shouldNotDetectFailureSpikeBelowMinimumFailureRate() {
        RollingWindowSnapshot snapshot = new RollingWindowSnapshot(Instant.EPOCH, Instant.EPOCH.plusSeconds(60), 50, 50, 10, 0, 0, 0.2, 0, 0, null, null, null, null, null, null, null, 0, 0, 0, null, 0, Instant.EPOCH);
        AnomalyContext context = new AnomalyContext(AnomalyTestFixtures.event("2026-06-23T00:00:00Z", 10), AnomalyTestFixtures.key(), null, null, AnomalyTestFixtures.behaviorBaseline(2), snapshot,
                null, new BehaviorDeviation(null, 99.0, null, null, null, null, null, null, null, null, null), new BaselineConfidence(false, true, true, true));

        var result = new BehavioralRuleEvaluator().evaluate(context);

        assertEquals(AnomalyDecision.NORMAL, result.decision());
        assertTrue(result.matches().isEmpty());
    }
}
