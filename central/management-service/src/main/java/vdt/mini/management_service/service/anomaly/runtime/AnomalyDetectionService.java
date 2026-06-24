package vdt.mini.management_service.service.anomaly.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vdt.mini.management_service.dto.event.*;
import vdt.mini.management_service.service.anomaly.baseline.BaselineQueryService;
import vdt.mini.management_service.service.anomaly.rolling.RollingWindowStore;
import vdt.mini.management_service.service.anomaly.rule.BehavioralRuleEvaluator;
import vdt.mini.management_service.service.anomaly.rule.CompositeRiskEvaluator;
import vdt.mini.management_service.service.anomaly.rule.CompositeRiskScorer;
import vdt.mini.management_service.service.anomaly.rule.HistoricalLogRuleEvaluator;
import vdt.mini.management_service.service.anomaly.rule.RuleSetVersion;
import vdt.mini.management_service.util.enums.AnomalyDecision;
import vdt.mini.management_service.util.enums.AnomalyType;
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
    private final CompositeRiskScorer compositeRiskScorer;
    private final CompositeRiskEvaluator compositeRiskEvaluator;
    private final AnomalyTypeResolver anomalyTypeResolver;
    private final AnomalySeverityResolver anomalySeverityResolver;
    private final IncidentDedupService incidentDedupService;
    private final AnomalyEventPublisher publisher;

    public AnomalyDetectionService(SecurityLogValidator validator,
                                   StaticResultContextParser staticResultContextParser,
                                   BaselineQueryService baselineQueryService,
                                   RollingWindowStore rollingWindowStore,
                                    DeviationCalculatorService deviationCalculatorService,
                                    HistoricalLogRuleEvaluator historicalLogRuleEvaluator,
                                    BehavioralRuleEvaluator behavioralRuleEvaluator,
                                    CompositeRiskScorer compositeRiskScorer,
                                    CompositeRiskEvaluator compositeRiskEvaluator,
                                    AnomalyTypeResolver anomalyTypeResolver,
                                    AnomalySeverityResolver anomalySeverityResolver,
                                    IncidentDedupService incidentDedupService,
                                    AnomalyEventPublisher publisher) {
        this.validator = validator;
        this.staticResultContextParser = staticResultContextParser;
        this.baselineQueryService = baselineQueryService;
        this.rollingWindowStore = rollingWindowStore;
        this.deviationCalculatorService = deviationCalculatorService;
        this.historicalLogRuleEvaluator = historicalLogRuleEvaluator;
        this.behavioralRuleEvaluator = behavioralRuleEvaluator;
        this.compositeRiskScorer = compositeRiskScorer;
        this.compositeRiskEvaluator = compositeRiskEvaluator;
        this.anomalyTypeResolver = anomalyTypeResolver;
        this.anomalySeverityResolver = anomalySeverityResolver;
        this.incidentDedupService = incidentDedupService;
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
            RiskScoreResult initialRiskScore = compositeRiskScorer.score(context, matches);
            RuleEvaluationResult composite = compositeRiskEvaluator.evaluate(context, initialRiskScore, matches);
            matches.addAll(composite.matches());
            RiskScoreResult finalRiskScore = compositeRiskScorer.scoreFinal(context, matches, initialRiskScore);
            AnomalyDecision decision = resolveDecision(historical, behavioral, composite);
            if (!matches.isEmpty()) {
                log.info("Anomaly matched key={} decision={} rules={} windowSamples={}", key,
                        decision, matches.stream().map(RuleMatch::ruleId).toList(),
                        snapshot == null ? 0 : snapshot.windowSampleCount());
                publish(event, context, matches, decision, finalRiskScore);
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

    private AnomalyDecision resolveDecision(RuleEvaluationResult historical, RuleEvaluationResult behavioral, RuleEvaluationResult composite) {
        if (historical.decision() == AnomalyDecision.ANOMALY || behavioral.decision() == AnomalyDecision.ANOMALY || composite.decision() == AnomalyDecision.ANOMALY) return AnomalyDecision.ANOMALY;
        if (historical.decision() == AnomalyDecision.SUSPICIOUS || behavioral.decision() == AnomalyDecision.SUSPICIOUS || composite.decision() == AnomalyDecision.SUSPICIOUS) return AnomalyDecision.SUSPICIOUS;
        if (historical.decision() == AnomalyDecision.OBSERVE || behavioral.decision() == AnomalyDecision.OBSERVE || composite.decision() == AnomalyDecision.OBSERVE) return AnomalyDecision.OBSERVE;
        return AnomalyDecision.NORMAL;
    }

    private void publish(SecurityLogEventMessage event, AnomalyContext context, List<RuleMatch> matches, AnomalyDecision decision, RiskScoreResult riskScore) {
        AnomalyType primaryType = anomalyTypeResolver.resolve(matches);
        if (primaryType == null) return;
        Instant now = Instant.now();
        RollingWindowSnapshot snapshot = context.rollingSnapshot();
        Map<String, Object> features = new LinkedHashMap<>();
        features.put("historicalDeviation", context.historicalDeviation());
        features.put("behaviorDeviation", context.behaviorDeviation());
        features.put("staticContext", context.staticContext());
        features.put("riskContributions", riskScore.contributions());
        features.put("sourceAlertSeverity", event.getAlertSeverity());
        features.put("severityInputs", Map.of("decision", decision, "sourceSeverityPoints", riskScore.sourceSeverityPoints(), "highestConfidence", highestConfidence(matches)));
        features.put("compositeMatches", matches.stream().filter(match -> match.ruleId().startsWith("COMPOSITE_")).map(RuleMatch::ruleId).toList());
        String severity = anomalySeverityResolver.resolve(decision, riskScore, highestConfidence(matches), context, matches);
        IncidentDedupResult dedupResult = incidentDedupService.deduplicate(context, primaryType, severity, riskScore.totalScore(), features, now);
        features.put("incidentDedup", Map.of("shouldPublish", dedupResult.shouldPublish(), "incidentId", dedupResult.incidentId() == null ? "" : dedupResult.incidentId(), "matchedCount", dedupResult.matchedCount()));
        if (!dedupResult.shouldPublish()) {
            log.info("Suppressing duplicate anomaly publish for incidentId={} key={} type={}", dedupResult.incidentId(), context.groupKey(), primaryType);
            return;
        }
        AnomalyEvent anomalyEvent = new AnomalyEvent(UUID.randomUUID().toString(), dedupResult.incidentId(), now, "LOG_RULE_ENGINE", primaryType, severity,
                event.getTraceId(), event.getCorrelationId(), event.getServiceId(), event.getServiceName(), event.getEndpointId(), event.getEndpointName(), event.getFlowType(), event.getDirection(),
                decision, riskScore.totalScore(), highestConfidence(matches), matches.stream().map(RuleMatch::ruleId).toList(),
                matches.stream().flatMap(match -> match.detectedFeatures().stream()).distinct().toList(), features, RuleSetVersion.CURRENT,
                context.logBaseline() == null ? null : context.logBaseline().baselineVersion(), context.behaviorBaseline() == null ? null : context.behaviorBaseline().baselineVersion(),
                snapshot == null ? null : snapshot.windowStart(), snapshot == null ? null : snapshot.windowEnd(), snapshot == null ? 0 : snapshot.windowSampleCount(),
                dedupResult.firstSeenAt(), dedupResult.lastSeenAt(), dedupResult.matchedCount(), now);
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
