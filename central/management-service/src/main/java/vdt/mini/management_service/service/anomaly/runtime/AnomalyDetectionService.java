package vdt.mini.management_service.service.anomaly.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vdt.mini.management_service.dto.event.*;
import vdt.mini.management_service.service.anomaly.baseline.BaselineQueryService;
import vdt.mini.management_service.service.anomaly.rolling.RollingWindowStore;
import vdt.mini.management_service.service.anomaly.rule.BehavioralRuleEvaluator;
import vdt.mini.management_service.service.anomaly.rule.HistoricalLogRuleEvaluator;
import vdt.mini.management_service.service.anomaly.rule.RuleSetVersion;
import vdt.mini.management_service.util.enums.AnomalyDecision;
import vdt.mini.management_service.util.enums.RuleConfidence;

import java.time.Instant;
import java.util.*;

@Service
public class AnomalyDetectionService {
    private static final Logger log = LoggerFactory.getLogger(AnomalyDetectionService.class);
    private final SecurityLogValidator validator;
    private final StaticResultContextParser staticResultContextParser;
    private final BaselineQueryService baselineQueryService;
    private final RollingWindowStore rollingWindowStore;
    private final DeviationCalculatorService deviationCalculatorService;
    private final HistoricalLogRuleEvaluator historicalLogRuleEvaluator;
    private final BehavioralRuleEvaluator behavioralRuleEvaluator;
    private final AnomalyEventPublisher publisher;

    public AnomalyDetectionService(SecurityLogValidator validator,
                                   StaticResultContextParser staticResultContextParser,
                                   BaselineQueryService baselineQueryService,
                                   RollingWindowStore rollingWindowStore,
                                   DeviationCalculatorService deviationCalculatorService,
                                   HistoricalLogRuleEvaluator historicalLogRuleEvaluator,
                                   BehavioralRuleEvaluator behavioralRuleEvaluator,
                                   AnomalyEventPublisher publisher) {
        this.validator = validator;
        this.staticResultContextParser = staticResultContextParser;
        this.baselineQueryService = baselineQueryService;
        this.rollingWindowStore = rollingWindowStore;
        this.deviationCalculatorService = deviationCalculatorService;
        this.historicalLogRuleEvaluator = historicalLogRuleEvaluator;
        this.behavioralRuleEvaluator = behavioralRuleEvaluator;
        this.publisher = publisher;
    }

    public void process(SecurityLogEventMessage event) {
        if (!validator.isValid(event)) {
            log.warn("Skipping invalid security log event");
            return;
        }
        AnomalyGroupKey key = event.groupKey();
        RollingWindowEntry entry = RollingWindowEntry.from(event);
        try {
            StaticResultContext staticContext = staticResultContextParser.parse(event);
            LogBaselineSnapshot logBaseline = safeLogBaseline(key).orElse(null);
            BehaviorBaselineSnapshot behaviorBaseline = safeBehaviorBaseline(key).orElse(null);
            RollingWindowSnapshot snapshot = rollingWindowStore.snapshotBefore(key, entry.timestamp());
            AnomalyContext context = deviationCalculatorService.calculate(event, key, staticContext, logBaseline, behaviorBaseline, snapshot);
            List<RuleMatch> matches = new ArrayList<>();
            RuleEvaluationResult historical = historicalLogRuleEvaluator.evaluate(context);
            RuleEvaluationResult behavioral = behavioralRuleEvaluator.evaluate(context);
            matches.addAll(historical.matches());
            matches.addAll(behavioral.matches());
            if (!matches.isEmpty()) {
                log.info("Anomaly matched key={} decision={} rules={} windowSamples={}", key,
                        resolveDecision(historical, behavioral), matches.stream().map(RuleMatch::ruleId).toList(),
                        snapshot == null ? 0 : snapshot.windowSampleCount());
                publish(event, context, matches, resolveDecision(historical, behavioral));
            }
        } catch (RuntimeException exception) {
            log.warn("Anomaly processing degraded for key={}", key, exception);
        } finally {
            rollingWindowStore.add(key, entry);
        }
    }

    private Optional<LogBaselineSnapshot> safeLogBaseline(AnomalyGroupKey key) {
        try { return baselineQueryService.findLogBaseline(key); } catch (RuntimeException ex) { log.warn("Log baseline lookup failed for key={}", key, ex); return Optional.empty(); }
    }

    private Optional<BehaviorBaselineSnapshot> safeBehaviorBaseline(AnomalyGroupKey key) {
        try { return baselineQueryService.findBehaviorBaseline(key); } catch (RuntimeException ex) { log.warn("Behavior baseline lookup failed for key={}", key, ex); return Optional.empty(); }
    }

    private AnomalyDecision resolveDecision(RuleEvaluationResult historical, RuleEvaluationResult behavioral) {
        if (historical.decision() == AnomalyDecision.ANOMALY || behavioral.decision() == AnomalyDecision.ANOMALY) return AnomalyDecision.ANOMALY;
        if (historical.decision() == AnomalyDecision.OBSERVE || behavioral.decision() == AnomalyDecision.OBSERVE) return AnomalyDecision.OBSERVE;
        return AnomalyDecision.NORMAL;
    }

    private void publish(SecurityLogEventMessage event, AnomalyContext context, List<RuleMatch> matches, AnomalyDecision decision) {
        RuleMatch primary = matches.getFirst();
        Instant now = Instant.now();
        RollingWindowSnapshot snapshot = context.rollingSnapshot();
        Map<String, Object> features = new LinkedHashMap<>();
        features.put("historicalDeviation", context.historicalDeviation());
        features.put("behaviorDeviation", context.behaviorDeviation());
        features.put("staticContext", context.staticContext());
        AnomalyEvent anomalyEvent = new AnomalyEvent(UUID.randomUUID().toString(), null, now, "LOG_RULE_ENGINE", primary.anomalyType(), "HIGH",
                event.getTraceId(), event.getCorrelationId(), event.getServiceId(), event.getServiceName(), event.getEndpointId(), event.getEndpointName(), event.getFlowType(), event.getDirection(),
                decision, matches.stream().mapToInt(RuleMatch::riskPoints).sum(), highestConfidence(matches), matches.stream().map(RuleMatch::ruleId).toList(),
                matches.stream().flatMap(match -> match.detectedFeatures().stream()).distinct().toList(), features, RuleSetVersion.CURRENT,
                context.logBaseline() == null ? null : context.logBaseline().baselineVersion(), context.behaviorBaseline() == null ? null : context.behaviorBaseline().baselineVersion(),
                snapshot == null ? null : snapshot.windowStart(), snapshot == null ? null : snapshot.windowEnd(), snapshot == null ? 0 : snapshot.windowSampleCount(), now, now, matches.size(), now);
        try {
            publisher.publish(anomalyEvent);
        } catch (RuntimeException exception) {
            log.warn("Failed to hand off anomaly event id={}", anomalyEvent.anomalyId(), exception);
        }
    }

    private RuleConfidence highestConfidence(List<RuleMatch> matches) {
        if (matches.stream().anyMatch(match -> match.confidence() == RuleConfidence.HIGH)) return RuleConfidence.HIGH;
        if (matches.stream().anyMatch(match -> match.confidence() == RuleConfidence.MEDIUM)) return RuleConfidence.MEDIUM;
        return RuleConfidence.LOW;
    }
}
