package vdt.mini.management_service.service.anomaly.rule;

import org.junit.jupiter.api.Test;
import vdt.mini.management_service.dto.event.*;
import vdt.mini.management_service.service.anomaly.runtime.AnomalyTestFixtures;
import vdt.mini.management_service.util.enums.AnomalyDecision;
import vdt.mini.management_service.util.enums.AnomalyType;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    @Test
    void behavioralEvaluator_shouldDetectTrafficSpikeFromExplicitRequestCountDeviation() {
        RollingWindowSnapshot snapshot = new RollingWindowSnapshot(Instant.EPOCH, Instant.EPOCH.plusSeconds(60), 100, 100, 0, 0, 0, 0, 0, 0, null, null, null, null, null, null, null, 1, 1, 0, null, 0, Instant.EPOCH);
        AnomalyContext context = new AnomalyContext(AnomalyTestFixtures.event("2026-06-23T00:00:00Z", 10), AnomalyTestFixtures.key(), null, null, AnomalyTestFixtures.behaviorBaseline(2), snapshot,
                null, new BehaviorDeviation(6.0, null, null, null, null, null, null, null, null, null, null), new BaselineConfidence(false, true, true, true));

        var result = new BehavioralRuleEvaluator().evaluate(context);

        assertEquals(AnomalyDecision.ANOMALY, result.decision());
        assertEquals(AnomalyType.TRAFFIC_SPIKE, result.matches().getFirst().anomalyType());
        assertEquals("BEHAVIOR_TRAFFIC_SPIKE_001", result.matches().getFirst().ruleId());
    }

    @Test
    void evaluators_shouldEmitAllDefinedAnomalyTypes() {
        Set<AnomalyType> emitted = new HashSet<>();
        HistoricalLogRuleEvaluator historicalEvaluator = new HistoricalLogRuleEvaluator();
        BehavioralRuleEvaluator behavioralEvaluator = new BehavioralRuleEvaluator();
        CompositeRiskEvaluator compositeEvaluator = new CompositeRiskEvaluator();
        BaselineConfidence confidence = new BaselineConfidence(true, true, true, true);

        AnomalyContext historicalContext = new AnomalyContext(AnomalyTestFixtures.event("2026-06-23T00:00:00Z", 10), AnomalyTestFixtures.key(), null, null, null, null,
                new HistoricalDeviation(6.0, 6.0, 6.0, 6.0, 5.0, true, true, true), null, confidence);
        emitted.addAll(historicalEvaluator.evaluate(historicalContext).matches().stream().map(RuleMatch::anomalyType).toList());

        RollingWindowSnapshot richSnapshot = new RollingWindowSnapshot(Instant.EPOCH, Instant.EPOCH.plusSeconds(60), 100, 100, 10, 10, 10,
                0.5, 0.5, 0.5, 200.0, 200.0, 200.0, 200.0, 200.0, 300.0, 400.0, 20, 20, 20, "E1", 0.5, Instant.EPOCH);
        BehaviorDeviation positiveBehavior = new BehaviorDeviation(6.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0);
        AnomalyContext behavioralContext = new AnomalyContext(AnomalyTestFixtures.event("2026-06-23T00:00:00Z", 10), AnomalyTestFixtures.key(), null, null,
                AnomalyTestFixtures.behaviorBaseline(2), richSnapshot, null, positiveBehavior, confidence);
        List<RuleMatch> behavioralMatches = behavioralEvaluator.evaluate(behavioralContext).matches();
        emitted.addAll(behavioralMatches.stream().map(RuleMatch::anomalyType).toList());

        BehaviorDeviation trafficDropBehavior = new BehaviorDeviation(-5.0, null, null, null, null, null, null, null, null, null, null);
        AnomalyContext trafficDropContext = new AnomalyContext(AnomalyTestFixtures.event("2026-06-23T00:00:00Z", 10), AnomalyTestFixtures.key(), null, null,
                AnomalyTestFixtures.behaviorBaseline(2), richSnapshot, null, trafficDropBehavior, confidence);
        emitted.addAll(behavioralEvaluator.evaluate(trafficDropContext).matches().stream().map(RuleMatch::anomalyType).toList());

        StaticResultContext staticContext = new StaticResultContext("DENIED", null, "AUTH_FAILURE", "AUTH_DENIED", null, 1, null, true, true, true);
        AnomalyContext compositeContext = new AnomalyContext(AnomalyTestFixtures.event("2026-06-23T00:00:00Z", 10), AnomalyTestFixtures.key(), staticContext, null,
                AnomalyTestFixtures.behaviorBaseline(2), richSnapshot, null, positiveBehavior, confidence);
        emitted.addAll(compositeEvaluator.evaluate(compositeContext, new RiskScoreResult(0, 0, Map.of()), behavioralMatches).matches().stream().map(RuleMatch::anomalyType).toList());

        assertEquals(new HashSet<>(Arrays.asList(AnomalyType.values())), emitted);
    }
}
